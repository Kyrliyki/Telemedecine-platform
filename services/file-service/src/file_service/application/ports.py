"""Ports implemented by infrastructure adapters."""

from __future__ import annotations

from collections.abc import AsyncIterator, Callable
from dataclasses import dataclass
from datetime import datetime
from typing import Any, Protocol
from uuid import UUID

from file_service.application.dto import Actor, FileInfo
from file_service.domain.events import DomainEvent
from file_service.domain.repositories import AttachmentRepository


class OutboxRepository(Protocol):
    async def add(self, event: DomainEvent) -> None: ...

    async def claim_batch(self, limit: int) -> list[OutboxMessage]: ...

    async def mark_published(self, message_id: UUID, published_at: datetime) -> None: ...

    async def record_failure(self, message_id: UUID, error: str, now: datetime) -> None: ...


@dataclass(frozen=True, slots=True)
class OutboxMessage:
    id: UUID
    event_type: str
    envelope: dict[str, Any]
    attempts: int


class UnitOfWork(Protocol):
    attachments: AttachmentRepository
    outbox: OutboxRepository

    async def commit(self) -> None: ...

    async def rollback(self) -> None: ...

    async def __aenter__(self) -> UnitOfWork: ...

    async def __aexit__(self, exc_type: object, exc: object, tb: object) -> None: ...


UnitOfWorkFactory = Callable[[], UnitOfWork]


@dataclass(frozen=True, slots=True)
class StoredObject:
    size_bytes: int
    etag: str | None = None
    checksum_sha256: str | None = None


class ObjectStorage(Protocol):
    async def ensure_bucket(self) -> None: ...

    async def presigned_put(self, storage_key: str, *, content_type: str, ttl_seconds: int) -> str: ...

    async def presigned_get(
        self,
        storage_key: str,
        *,
        ttl_seconds: int,
        download_name: str | None = None,
    ) -> str: ...

    async def put_stream(
        self,
        storage_key: str,
        stream: AsyncIterator[bytes],
        *,
        content_type: str,
        max_bytes: int,
    ) -> StoredObject: ...

    async def stat(self, storage_key: str) -> StoredObject | None: ...

    async def delete(self, storage_key: str) -> None: ...

    async def healthy(self) -> bool: ...


class FileCache(Protocol):
    async def get(self, file_id: UUID) -> FileInfo | None: ...

    async def set(self, info: FileInfo, *, ttl_seconds: int) -> None: ...

    async def delete(self, file_id: UUID) -> None: ...

    async def healthy(self) -> bool: ...


class Clock(Protocol):
    def now(self) -> datetime: ...


class IdGenerator(Protocol):
    def new_id(self) -> UUID: ...


class ChatAccessPolicy(Protocol):
    async def can_access(self, actor: Actor, chat_id: UUID) -> bool: ...

    async def aclose(self) -> None: ...
