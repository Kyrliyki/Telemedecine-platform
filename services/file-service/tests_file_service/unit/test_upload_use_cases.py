"""Upload use case tests (direct and presigned flows)."""

from __future__ import annotations

from uuid import uuid4

import pytest

from file_service.application.dto import (
    Actor,
    CompleteUploadCommand,
    DeleteFileCommand,
    GetFileQuery,
    InitUploadCommand,
    ListChatFilesQuery,
    UploadFileCommand,
)
from file_service.domain.enums import FileStatus
from file_service.domain.exceptions import ConflictError, FileTooLargeError, ForbiddenError, NotFoundError
from tests_file_service.fakes import FakeContext, collect_stream


def actor(role: str = "patient") -> Actor:
    return Actor(id=uuid4(), roles=frozenset({role}))

def direct_command(act: Actor, chat_id, data: bytes = b"hello world", **kwargs) -> UploadFileCommand:
    return UploadFileCommand(
        actor=act,
        chat_id=chat_id,
        file_name=kwargs.pop("file_name", "scan.pdf"),
        content_type=kwargs.pop("content_type", "application/pdf"),
        stream=collect_stream(data),
        declared_size=kwargs.pop("declared_size", len(data)),
        **kwargs,
    )

async def test_direct_upload_creates_ready_file_and_event(fake_context: FakeContext) -> None:
    act = actor()
    chat_id = uuid4()

    view = await fake_context.use_cases.upload_file(direct_command(act, chat_id))

    assert view.info.status is FileStatus.READY
    assert view.info.size_bytes == 11
    assert view.info.checksum_sha256
    assert view.download_url and view.download_url.startswith("https://storage.test/download/")
    assert view.info.uploader_id == act.id
    stored = fake_context.db.storage.objects[view.info.storage_key]
    assert stored[0] == b"hello world"
    assert [message.event_type for message in fake_context.db.outbox] == ["file.uploaded"]
    envelope = fake_context.db.outbox[0].envelope
    assert envelope["payload"]["file_id"] == str(view.info.id)
    assert envelope["producer"] == "file-service"

async def test_failed_storage_upload_marks_attachment_failed(fake_context: FakeContext) -> None:
    act = actor()
    chat_id = uuid4()
    fake_context.db.storage.fail_next = True

    with pytest.raises(OSError):
        await fake_context.use_cases.upload_file(direct_command(act, chat_id))

    assert len(fake_context.db.attachments) == 1
    attachment = next(iter(fake_context.db.attachments.values()))
    assert attachment.status is FileStatus.FAILED
    assert fake_context.db.outbox == []
    assert fake_context.db.storage.deleted

async def test_upload_rejects_forbidden_chat() -> None:
    from tests_file_service.fakes import build_fake_context

    context = build_fake_context(allowed=False)
    with pytest.raises(ForbiddenError):
        await context.use_cases.upload_file(direct_command(actor(), uuid4()))
    assert context.db.attachments == {}

async def test_upload_rejects_oversized_file_before_storage() -> None:
    from tests_file_service.fakes import build_fake_context

    context = build_fake_context(max_size_bytes=5)
    with pytest.raises(FileTooLargeError):
        await context.use_cases.upload_file(direct_command(actor(), uuid4(), data=b"123456"))
    assert context.db.attachments == {}

async def test_stream_limit_is_enforced() -> None:
    from tests_file_service.fakes import build_fake_context

    context = build_fake_context(max_size_bytes=5)
    command = direct_command(actor(), uuid4(), data=b"x" * 20, declared_size=None)
    with pytest.raises(FileTooLargeError):
        await context.use_cases.upload_file(command)

    attachment = next(iter(context.db.attachments.values()))
    assert attachment.status is FileStatus.FAILED

async def test_presigned_upload_lifecycle(fake_context: FakeContext) -> None:
    act = actor()
    chat_id = uuid4()
    ticket = await fake_context.use_cases.init_upload(
        InitUploadCommand(
            actor=act,
            chat_id=chat_id,
            file_name="Результаты анализов.pdf",
            content_type="application/pdf",
            size_bytes=5,
        )
    )

    assert ticket.info.status is FileStatus.PENDING
    assert "upload/" in ticket.upload_url
    assert ticket.headers["Content-Type"] == "application/pdf"

    attachment = fake_context.db.attachments[ticket.info.id]
    fake_context.db.storage.put_bytes(attachment.storage_key, b"12345")

    view = await fake_context.use_cases.complete_upload(
        CompleteUploadCommand(actor=act, file_id=attachment.id)
    )
    assert view.info.status is FileStatus.READY
    assert view.info.size_bytes == 5
    assert [message.event_type for message in fake_context.db.outbox] == ["file.uploaded"]

    
    await fake_context.use_cases.complete_upload(CompleteUploadCommand(actor=act, file_id=attachment.id))
    assert len(fake_context.db.outbox) == 1

async def test_complete_upload_without_object_is_conflict(fake_context: FakeContext) -> None:
    act = actor()
    ticket = await fake_context.use_cases.init_upload(
        InitUploadCommand(
            actor=act,
            chat_id=uuid4(),
            file_name="a.pdf",
            content_type="application/pdf",
            size_bytes=5,
        )
    )
    with pytest.raises(ConflictError):
        await fake_context.use_cases.complete_upload(CompleteUploadCommand(actor=act, file_id=ticket.info.id))

async def test_history_pagination_and_cache(fake_context: FakeContext) -> None:
    act = actor()
    chat_id = uuid4()
    created = []
    for index in range(5):
        view = await fake_context.use_cases.upload_file(
            direct_command(act, chat_id, data=f"file-{index}".encode(), file_name=f"{index}.pdf")
        )
        created.append(view.info.id)
        fake_context.db.clock.advance(1)

    first = await fake_context.use_cases.list_chat_files(
        ListChatFilesQuery(actor=act, chat_id=chat_id, limit=2)
    )
    assert [item.info.id for item in first.items] == [created[4], created[3]]
    assert first.next_cursor

    second = await fake_context.use_cases.list_chat_files(
        ListChatFilesQuery(actor=act, chat_id=chat_id, limit=2, cursor=first.next_cursor)
    )
    assert [item.info.id for item in second.items] == [created[2], created[1]]
    assert second.next_cursor

    third = await fake_context.use_cases.list_chat_files(
        ListChatFilesQuery(actor=act, chat_id=chat_id, limit=2, cursor=second.next_cursor)
    )
    assert [item.info.id for item in third.items] == [created[0]]
    assert third.next_cursor is None

    
    file_id = created[4]
    assert file_id not in fake_context.db.cache.items
    await fake_context.use_cases.get_file(GetFileQuery(actor=act, file_id=file_id))
    assert file_id in fake_context.db.cache.items

async def test_delete_permissions_and_storage(fake_context: FakeContext) -> None:
    uploader = actor()
    other = actor()
    chat_id = uuid4()
    view = await fake_context.use_cases.upload_file(direct_command(uploader, chat_id))

    with pytest.raises(ForbiddenError):
        await fake_context.use_cases.delete_file(DeleteFileCommand(actor=other, file_id=view.info.id))

    assert (
        await fake_context.use_cases.delete_file(DeleteFileCommand(actor=uploader, file_id=view.info.id))
        is True
    )
    assert fake_context.db.attachments[view.info.id].status is FileStatus.DELETED
    assert view.info.storage_key in fake_context.db.storage.deleted
    assert [message.event_type for message in fake_context.db.outbox] == ["file.uploaded", "file.deleted"]

    with pytest.raises(NotFoundError):
        await fake_context.use_cases.get_file(GetFileQuery(actor=uploader, file_id=view.info.id))

async def test_moderator_can_delete_foreign_file(fake_context: FakeContext) -> None:
    uploader = actor()
    moderator = actor("moderator")
    view = await fake_context.use_cases.upload_file(direct_command(uploader, uuid4()))
    assert await fake_context.use_cases.delete_file(DeleteFileCommand(actor=moderator, file_id=view.info.id))

async def test_purge_chat_files(fake_context: FakeContext) -> None:
    act = actor()
    chat_id = uuid4()
    first = await fake_context.use_cases.upload_file(direct_command(act, chat_id, data=b"one"))
    second = await fake_context.use_cases.upload_file(direct_command(act, chat_id, data=b"two"))
    other_chat = await fake_context.use_cases.upload_file(direct_command(act, uuid4(), data=b"three"))

    purged = await fake_context.use_cases.purge_chat_files(chat_id)

    assert purged == 2
    assert fake_context.db.attachments[first.info.id].status is FileStatus.DELETED
    assert fake_context.db.attachments[second.info.id].status is FileStatus.DELETED
    assert fake_context.db.attachments[other_chat.info.id].status is FileStatus.READY
    assert first.info.storage_key in fake_context.db.storage.deleted
    assert sum(1 for message in fake_context.db.outbox if message.event_type == "file.deleted") == 2
