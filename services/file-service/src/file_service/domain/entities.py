"""Attachment aggregate root."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any
from uuid import UUID

from file_service.domain.enums import FileKind, FileStatus
from file_service.domain.events import FileDeleted, FileUploaded
from file_service.domain.exceptions import ConflictError, ForbiddenError, ValidationError

MODERATOR_ROLES = frozenset({"admin", "moderator"})


@dataclass(slots=True)
class Attachment:
    """A file attached to a chat. The actual bytes live in S3/MinIO."""

    id: UUID
    chat_id: UUID
    uploader_id: UUID
    file_name: str
    content_type: str
    kind: FileKind
    size_bytes: int
    storage_key: str
    bucket: str
    status: FileStatus
    created_at: datetime
    updated_at: datetime
    checksum_sha256: str | None = None
    message_id: UUID | None = None
    deleted_at: datetime | None = None
    metadata: dict[str, Any] = field(default_factory=dict)

    @classmethod
    def create_pending(
        cls,
        *,
        file_id: UUID,
        chat_id: UUID,
        uploader_id: UUID,
        file_name: str,
        content_type: str,
        kind: FileKind,
        declared_size: int,
        storage_key: str,
        bucket: str,
        now: datetime,
        message_id: UUID | None = None,
    ) -> Attachment:
        return cls(
            id=file_id,
            chat_id=chat_id,
            uploader_id=uploader_id,
            file_name=file_name,
            content_type=content_type,
            kind=kind,
            size_bytes=declared_size,
            storage_key=storage_key,
            bucket=bucket,
            status=FileStatus.PENDING,
            created_at=now,
            updated_at=now,
            message_id=message_id,
        )

    @property
    def is_ready(self) -> bool:
        return self.status is FileStatus.READY

    @property
    def is_deleted(self) -> bool:
        return self.status is FileStatus.DELETED

    def mark_ready(self, *, size_bytes: int, checksum_sha256: str | None, now: datetime) -> None:
        if self.status is not FileStatus.PENDING:
            raise ConflictError(f"Cannot complete upload in status '{self.status}'")
        if size_bytes <= 0:
            raise ValidationError("Uploaded file is empty")
        self.status = FileStatus.READY
        self.size_bytes = size_bytes
        self.checksum_sha256 = checksum_sha256
        self.updated_at = now

    def mark_failed(self, *, now: datetime, reason: str | None = None) -> None:
        if self.status is not FileStatus.PENDING:
            return
        self.status = FileStatus.FAILED
        self.updated_at = now
        if reason:
            self.metadata["failure_reason"] = reason

    def soft_delete(self, *, actor_id: UUID, roles: frozenset[str], now: datetime) -> bool:
        if self.status is FileStatus.DELETED:
            return False
        if actor_id != self.uploader_id and not (roles & MODERATOR_ROLES):
            raise ForbiddenError("Only the uploader or a moderator can delete this file")
        self._expire(now)
        return True

    def expire(self, *, now: datetime) -> bool:
        """System initiated deletion (chat removed) without permission checks."""
        if self.status is FileStatus.DELETED:
            return False
        self._expire(now)
        return True

    def _expire(self, now: datetime) -> None:
        self.status = FileStatus.DELETED
        self.deleted_at = now
        self.updated_at = now

    def uploaded_event(self) -> FileUploaded:
        if not self.is_ready:
            raise ConflictError("Only ready files can produce an upload event")
        return FileUploaded(
            file_id=self.id,
            chat_id=self.chat_id,
            uploader_id=self.uploader_id,
            file_name=self.file_name,
            content_type=self.content_type,
            kind=self.kind.value,
            size_bytes=self.size_bytes,
            checksum_sha256=self.checksum_sha256,
        )

    def deleted_event(self, *, deleted_by: UUID | None) -> FileDeleted:
        return FileDeleted(file_id=self.id, chat_id=self.chat_id, deleted_by=deleted_by)
