"""Domain events produced by the FileService."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any, ClassVar
from uuid import UUID, uuid4

EVENT_VERSION = 1
PRODUCER = "file-service"


@dataclass(frozen=True, slots=True)
class DomainEvent:
    event_type: ClassVar[str] = "domain.event"

    event_id: UUID = field(default_factory=uuid4)
    occurred_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def payload(self) -> dict[str, Any]:  # pragma: no cover - overridden
        raise NotImplementedError

    def envelope(self) -> dict[str, Any]:
        return {
            "event_id": str(self.event_id),
            "event_type": self.event_type,
            "version": EVENT_VERSION,
            "producer": PRODUCER,
            "occurred_at": self.occurred_at.isoformat(),
            "payload": self.payload(),
        }


@dataclass(frozen=True, slots=True)
class FileUploaded(DomainEvent):
    event_type: ClassVar[str] = "file.uploaded"

    file_id: UUID = field(default_factory=uuid4)
    chat_id: UUID = field(default_factory=uuid4)
    uploader_id: UUID = field(default_factory=uuid4)
    file_name: str = ""
    content_type: str = "application/octet-stream"
    kind: str = "other"
    size_bytes: int = 0
    checksum_sha256: str | None = None

    def payload(self) -> dict[str, Any]:
        return {
            "file_id": str(self.file_id),
            "chat_id": str(self.chat_id),
            "uploader_id": str(self.uploader_id),
            "file_name": self.file_name,
            "content_type": self.content_type,
            "kind": self.kind,
            "size_bytes": self.size_bytes,
            "checksum_sha256": self.checksum_sha256,
        }


@dataclass(frozen=True, slots=True)
class FileDeleted(DomainEvent):
    event_type: ClassVar[str] = "file.deleted"

    file_id: UUID = field(default_factory=uuid4)
    chat_id: UUID = field(default_factory=uuid4)
    deleted_by: UUID | None = None

    def payload(self) -> dict[str, Any]:
        return {
            "file_id": str(self.file_id),
            "chat_id": str(self.chat_id),
            "deleted_by": str(self.deleted_by) if self.deleted_by else None,
        }
