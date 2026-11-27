"""File-service events.

Producer: ``file-service``.
Exchange: ``file.events``.

Object bytes live in MinIO/S3; events contain only metadata. Consumers must
never assume that a temporary download URL is permanent.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from .base import EventPayload


class FileEventTypes:
    UPLOADED = "file.file.uploaded.v1"
    DELETED = "file.file.deleted.v1"


class FileUploadedPayload(EventPayload):
    file_id: UUID
    owner_id: UUID
    bucket: str
    object_key: str
    content_type: str
    size_bytes: int
    checksum_sha256: str | None = None
    uploaded_at: datetime


class FileDeletedPayload(EventPayload):
    file_id: UUID
    owner_id: UUID
    bucket: str
    object_key: str
    deleted_at: datetime


FILE_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    FileEventTypes.UPLOADED: FileUploadedPayload,
    FileEventTypes.DELETED: FileDeletedPayload,
}
