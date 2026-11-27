"""Application DTOs shared by use cases and the presentation layer."""

from __future__ import annotations

from collections.abc import AsyncIterator
from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from file_service.domain.entities import Attachment
from file_service.domain.enums import FileKind, FileStatus


@dataclass(frozen=True, slots=True)
class Actor:
    id: UUID
    roles: frozenset[str] = frozenset()

    @property
    def is_moderator(self) -> bool:
        return bool(self.roles & {"admin", "moderator"})


@dataclass(frozen=True, slots=True)
class FileInfo:
    """Cacheable file metadata without short-living URLs."""

    id: UUID
    chat_id: UUID
    uploader_id: UUID
    file_name: str
    content_type: str
    kind: FileKind
    size_bytes: int
    status: FileStatus
    storage_key: str
    checksum_sha256: str | None
    message_id: UUID | None
    created_at: datetime

    @classmethod
    def from_attachment(cls, attachment: Attachment) -> FileInfo:
        return cls(
            id=attachment.id,
            chat_id=attachment.chat_id,
            uploader_id=attachment.uploader_id,
            file_name=attachment.file_name,
            content_type=attachment.content_type,
            kind=attachment.kind,
            size_bytes=attachment.size_bytes,
            status=attachment.status,
            storage_key=attachment.storage_key,
            checksum_sha256=attachment.checksum_sha256,
            message_id=attachment.message_id,
            created_at=attachment.created_at,
        )


@dataclass(frozen=True, slots=True)
class FileView:
    info: FileInfo
    download_url: str | None = None
    download_url_expires_at: datetime | None = None


@dataclass(frozen=True, slots=True)
class FilePage:
    items: list[FileView]
    next_cursor: str | None


@dataclass(frozen=True, slots=True)
class UploadTicket:
    info: FileInfo
    upload_url: str
    method: str
    headers: dict[str, str]
    expires_at: datetime


@dataclass(frozen=True, slots=True)
class UploadFileCommand:
    actor: Actor
    chat_id: UUID
    file_name: str | None
    content_type: str | None
    stream: AsyncIterator[bytes]
    declared_size: int | None = None
    message_id: UUID | None = None


@dataclass(frozen=True, slots=True)
class InitUploadCommand:
    actor: Actor
    chat_id: UUID
    file_name: str
    content_type: str | None
    size_bytes: int
    message_id: UUID | None = None


@dataclass(frozen=True, slots=True)
class CompleteUploadCommand:
    actor: Actor
    file_id: UUID
    checksum_sha256: str | None = None


@dataclass(frozen=True, slots=True)
class ListChatFilesQuery:
    actor: Actor
    chat_id: UUID
    limit: int = 30
    cursor: str | None = None


@dataclass(frozen=True, slots=True)
class GetFileQuery:
    actor: Actor
    file_id: UUID
    as_download: bool = False


@dataclass(frozen=True, slots=True)
class DeleteFileCommand:
    actor: Actor
    file_id: UUID
