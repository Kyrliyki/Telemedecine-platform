"""In-memory fakes for unit and API tests."""

from __future__ import annotations

import hashlib
from collections.abc import AsyncIterator
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

from file_service.application.dto import Actor, FileInfo
from file_service.application.ports import OutboxMessage, StoredObject
from file_service.application.use_cases import UseCases, build_use_cases
from file_service.application.use_cases.common import AppDeps
from file_service.domain.entities import Attachment
from file_service.domain.enums import FileStatus
from file_service.domain.events import DomainEvent
from file_service.domain.exceptions import FileTooLargeError, ValidationError
from file_service.domain.value_objects import UploadPolicy


class FixedClock:
    def __init__(self, now: datetime | None = None) -> None:
        self._now = now or datetime(2026, 1, 15, 10, 0, tzinfo=UTC)

    def now(self) -> datetime:
        return self._now

    def advance(self, seconds: float = 1) -> None:
        self._now += timedelta(seconds=seconds)


class SequenceIds:
    def __init__(self) -> None:
        self._ids: list[UUID] = []

    def new_id(self) -> UUID:
        value = uuid4()
        self._ids.append(value)
        return value


class FakeChatAccess:
    def __init__(self, *, allowed: bool = True) -> None:
        self.allowed = allowed
        self.calls: list[tuple[UUID, UUID]] = []

    async def can_access(self, actor: Actor, chat_id: UUID) -> bool:
        self.calls.append((actor.id, chat_id))
        return self.allowed

    async def aclose(self) -> None:
        return None


class FakeStorage:
    def __init__(self) -> None:
        self.objects: dict[str, tuple[bytes, str]] = {}
        self.fail_next = False
        self.deleted: list[str] = []

    async def ensure_bucket(self) -> None:
        return None

    async def presigned_put(self, storage_key: str, *, content_type: str, ttl_seconds: int) -> str:
        return f"https://storage.test/upload/{storage_key}?ttl={ttl_seconds}&type={content_type}"

    async def presigned_get(
        self,
        storage_key: str,
        *,
        ttl_seconds: int,
        download_name: str | None = None,
    ) -> str:
        suffix = f"&download={download_name}" if download_name else ""
        return f"https://storage.test/download/{storage_key}?ttl={ttl_seconds}{suffix}"

    async def put_stream(
        self,
        storage_key: str,
        stream: AsyncIterator[bytes],
        *,
        content_type: str,
        max_bytes: int,
    ) -> StoredObject:
        if self.fail_next:
            self.fail_next = False
            raise OSError("storage is down")
        data = bytearray()
        async for chunk in stream:
            data.extend(chunk)
            if len(data) > max_bytes:
                raise FileTooLargeError("too large")
        if not data:
            raise ValidationError("File is empty")
        checksum = hashlib.sha256(data).hexdigest()
        self.objects[storage_key] = (bytes(data), checksum)
        return StoredObject(size_bytes=len(data), checksum_sha256=checksum, etag=checksum[:16])

    def put_bytes(self, storage_key: str, data: bytes, checksum: str | None = None) -> None:
        self.objects[storage_key] = (data, checksum or hashlib.sha256(data).hexdigest())

    async def stat(self, storage_key: str) -> StoredObject | None:
        stored = self.objects.get(storage_key)
        if stored is None:
            return None
        data, checksum = stored
        return StoredObject(size_bytes=len(data), checksum_sha256=checksum)

    async def delete(self, storage_key: str) -> None:
        self.deleted.append(storage_key)
        self.objects.pop(storage_key, None)

    async def healthy(self) -> bool:
        return True


class FakeFileCache:
    def __init__(self) -> None:
        self.items: dict[UUID, FileInfo] = {}

    async def get(self, file_id: UUID) -> FileInfo | None:
        return self.items.get(file_id)

    async def set(self, info: FileInfo, *, ttl_seconds: int) -> None:
        self.items[info.id] = info

    async def delete(self, file_id: UUID) -> None:
        self.items.pop(file_id, None)

    async def healthy(self) -> bool:
        return True


@dataclass
class FakeDatabase:
    attachments: dict[UUID, Attachment] = field(default_factory=dict)
    outbox: list[OutboxMessage] = field(default_factory=list)
    storage: FakeStorage = field(default_factory=FakeStorage)
    cache: FakeFileCache = field(default_factory=FakeFileCache)
    clock: FixedClock = field(default_factory=FixedClock)

    def uow_factory(self) -> FakeUnitOfWork:
        return FakeUnitOfWork(self)


class FakeAttachmentRepository:
    def __init__(self, db: FakeDatabase) -> None:
        self._db = db

    async def add(self, attachment: Attachment) -> None:
        self._db.attachments[attachment.id] = attachment

    async def get(self, file_id: UUID, *, for_update: bool = False) -> Attachment | None:
        return self._db.attachments.get(file_id)

    async def update(self, attachment: Attachment) -> None:
        self._db.attachments[attachment.id] = attachment

    async def list_by_chat(
        self,
        chat_id: UUID,
        *,
        limit: int,
        before: tuple[datetime, UUID] | None = None,
    ) -> list[Attachment]:
        rows = [
            attachment
            for attachment in self._db.attachments.values()
            if attachment.chat_id == chat_id and attachment.status is FileStatus.READY
        ]
        if before is not None:
            rows = [row for row in rows if (row.created_at, row.id) < before]
        rows.sort(key=lambda row: (row.created_at, row.id), reverse=True)
        return rows[:limit]

    async def list_all_by_chat(self, chat_id: UUID) -> list[Attachment]:
        return [attachment for attachment in self._db.attachments.values() if attachment.chat_id == chat_id]


class FakeOutboxRepository:
    def __init__(self, db: FakeDatabase) -> None:
        self._db = db

    async def add(self, event: DomainEvent) -> None:
        self._db.outbox.append(
            OutboxMessage(
                id=event.event_id,
                event_type=event.event_type,
                envelope=event.envelope(),
                attempts=0,
            )
        )

    async def claim_batch(self, limit: int) -> list[OutboxMessage]:
        return self._db.outbox[:limit]

    async def mark_published(self, message_id: UUID, published_at: datetime) -> None:
        self._db.outbox = [message for message in self._db.outbox if message.id != message_id]

    async def record_failure(self, message_id: UUID, error: str, now: datetime) -> None:
        for index, message in enumerate(self._db.outbox):
            if message.id == message_id:
                self._db.outbox[index] = OutboxMessage(
                    id=message.id,
                    event_type=message.event_type,
                    envelope=message.envelope,
                    attempts=message.attempts + 1,
                )


class FakeUnitOfWork:
    def __init__(self, db: FakeDatabase) -> None:
        self._db = db
        self.attachments = FakeAttachmentRepository(db)
        self.outbox = FakeOutboxRepository(db)
        self.committed = False

    async def __aenter__(self) -> FakeUnitOfWork:
        return self

    async def __aexit__(self, exc_type: object, exc: object, tb: object) -> None:
        return None

    async def commit(self) -> None:
        self.committed = True

    async def rollback(self) -> None:
        return None


@dataclass
class FakeContext:
    db: FakeDatabase
    deps: AppDeps
    use_cases: UseCases
    chat_access: FakeChatAccess


def build_fake_context(
    *,
    allowed: bool = True,
    max_size_bytes: int = 100 * 1024 * 1024,
    allowed_content_types: frozenset[str] | None = None,
) -> FakeContext:
    db = FakeDatabase()
    chat_access = FakeChatAccess(allowed=allowed)
    deps = AppDeps(
        uow_factory=db.uow_factory,
        storage=db.storage,
        cache=db.cache,
        clock=db.clock,
        ids=SequenceIds(),
        policy=UploadPolicy(max_size_bytes=max_size_bytes, allowed_content_types=allowed_content_types),
        chat_access=chat_access,
        bucket="test-files",
        presign_ttl_seconds=900,
        cache_ttl_seconds=60,
    )
    return FakeContext(db=db, deps=deps, use_cases=build_use_cases(deps), chat_access=chat_access)


async def collect_stream(data: bytes, chunk_size: int = 7) -> AsyncIterator[bytes]:
    for offset in range(0, len(data), chunk_size):
        yield data[offset : offset + chunk_size]
