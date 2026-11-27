"""Domain entity and policy tests."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest

from file_service.domain.entities import Attachment
from file_service.domain.enums import FileKind, FileStatus
from file_service.domain.exceptions import ConflictError, FileTooLargeError, ForbiddenError, ValidationError
from file_service.domain.value_objects import (
    UploadPolicy,
    build_storage_key,
    classify_content_type,
    normalize_file_name,
)

NOW = datetime(2026, 1, 1, 12, 0, tzinfo=UTC)
CHAT_ID = uuid4()
UPLOADER_ID = uuid4()


def make_attachment(*, status: FileStatus = FileStatus.PENDING) -> Attachment:
    attachment = Attachment.create_pending(
        file_id=uuid4(),
        chat_id=CHAT_ID,
        uploader_id=UPLOADER_ID,
        file_name="scan.pdf",
        content_type="application/pdf",
        kind=FileKind.DOCUMENT,
        declared_size=100,
        storage_key="chats/x/file.pdf",
        bucket="chat-files",
        now=NOW,
    )
    attachment.status = status
    return attachment


def test_mark_ready_transitions_from_pending() -> None:
    attachment = make_attachment()
    attachment.mark_ready(size_bytes=120, checksum_sha256="a" * 64, now=NOW + timedelta(seconds=5))

    assert attachment.status is FileStatus.READY
    assert attachment.size_bytes == 120
    assert attachment.checksum_sha256 == "a" * 64
    assert attachment.updated_at == NOW + timedelta(seconds=5)
    event = attachment.uploaded_event()
    assert event.file_id == attachment.id
    assert event.size_bytes == 120


def test_mark_ready_twice_is_conflict() -> None:
    attachment = make_attachment(status=FileStatus.READY)
    with pytest.raises(ConflictError):
        attachment.mark_ready(size_bytes=10, checksum_sha256=None, now=NOW)


def test_mark_ready_rejects_empty_file() -> None:
    attachment = make_attachment()
    with pytest.raises(ValidationError):
        attachment.mark_ready(size_bytes=0, checksum_sha256=None, now=NOW)


def test_failed_upload_does_not_override_ready() -> None:
    attachment = make_attachment(status=FileStatus.READY)
    attachment.mark_failed(now=NOW, reason="boom")
    assert attachment.status is FileStatus.READY


def test_only_uploader_or_moderator_can_delete() -> None:
    attachment = make_attachment(status=FileStatus.READY)
    with pytest.raises(ForbiddenError):
        attachment.soft_delete(actor_id=uuid4(), roles=frozenset({"patient"}), now=NOW)

    moderator = uuid4()
    assert attachment.soft_delete(actor_id=moderator, roles=frozenset({"moderator"}), now=NOW) is True
    assert attachment.status is FileStatus.DELETED
    assert attachment.deleted_at == NOW
    assert attachment.soft_delete(actor_id=UPLOADER_ID, roles=frozenset(), now=NOW) is False


def test_uploader_can_delete_own_file() -> None:
    attachment = make_attachment(status=FileStatus.READY)
    assert attachment.soft_delete(actor_id=UPLOADER_ID, roles=frozenset(), now=NOW) is True


def test_system_expire_has_no_permission_check() -> None:
    attachment = make_attachment(status=FileStatus.READY)
    assert attachment.expire(now=NOW) is True
    assert attachment.status is FileStatus.DELETED
    assert attachment.expire(now=NOW) is False


def test_uploaded_event_requires_ready_status() -> None:
    with pytest.raises(ConflictError):
        make_attachment().uploaded_event()


def test_policy_rejects_oversized_file() -> None:
    policy = UploadPolicy(max_size_bytes=10)
    with pytest.raises(FileTooLargeError):
        policy.prepare(file_name="a.txt", content_type="text/plain", size_bytes=11)


def test_policy_accepts_content_type_masks() -> None:
    policy = UploadPolicy(allowed_content_types=frozenset({"image/*", "application/pdf"}))
    assert policy.prepare(file_name="a.png", content_type="image/png", size_bytes=1).kind is FileKind.IMAGE
    assert (
        policy.prepare(file_name="a.pdf", content_type="application/pdf", size_bytes=1).kind
        is FileKind.DOCUMENT
    )
    with pytest.raises(ValidationError):
        policy.prepare(file_name="a.exe", content_type="application/x-msdownload", size_bytes=1)


def test_file_name_is_sanitized() -> None:
    assert normalize_file_name("../../etc/passwd") == "passwd"
    assert normalize_file_name("C:\\temp\\scan.pdf") == "scan.pdf"
    assert normalize_file_name("  анализ  крови.pdf  ") == "анализ  крови.pdf"
    with pytest.raises(ValidationError):
        normalize_file_name("")
    with pytest.raises(ValidationError):
        normalize_file_name("..")


def test_content_type_classification() -> None:
    assert classify_content_type("image/jpeg") is FileKind.IMAGE
    assert classify_content_type("video/mp4") is FileKind.VIDEO
    assert classify_content_type("audio/mpeg") is FileKind.AUDIO
    assert classify_content_type("application/pdf") is FileKind.DOCUMENT
    assert classify_content_type("application/zip") is FileKind.ARCHIVE
    assert classify_content_type("application/x-msdownload") is FileKind.OTHER


def test_storage_key_is_opaque_and_keeps_extension() -> None:
    file_id = uuid4()
    key = build_storage_key(CHAT_ID, file_id, "МРТ колено.PDF")
    assert key == f"chats/{CHAT_ID}/{file_id.hex}.pdf"
    assert "МРТ" not in key
