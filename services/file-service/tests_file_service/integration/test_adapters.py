"""Integration tests against real PostgreSQL and MinIO started by Testcontainers."""

from __future__ import annotations

import hashlib
import uuid
from datetime import UTC, datetime, timedelta

import httpx
import pytest
from sqlalchemy import delete
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from file_service.domain.entities import Attachment
from file_service.domain.enums import FileKind, FileStatus
from file_service.domain.events import FileUploaded
from file_service.infrastructure.db.models import Base, FileAttachmentModel, OutboxMessageModel
from file_service.infrastructure.db.uow import SqlAlchemyUnitOfWork
from file_service.infrastructure.storage.minio_storage import MinioObjectStorage
from tests_file_service.fakes import collect_stream

pytestmark = pytest.mark.integration


def make_attachment(chat_id: uuid.UUID, created_at: datetime, *, status: FileStatus = FileStatus.READY):
    file_id = uuid.uuid4()
    attachment = Attachment.create_pending(
        file_id=file_id,
        chat_id=chat_id,
        uploader_id=uuid.uuid4(),
        file_name="scan.pdf",
        content_type="application/pdf",
        kind=FileKind.DOCUMENT,
        declared_size=10,
        storage_key=f"tests/{file_id}.pdf",
        bucket="test",
        now=created_at,
    )
    attachment.status = status
    if status is FileStatus.READY:
        attachment.size_bytes = 10
        attachment.checksum_sha256 = hashlib.sha256(b"0123456789").hexdigest()
    return attachment


@pytest.fixture
async def session_factory(database_url: str):
    engine = create_async_engine(database_url, pool_pre_ping=True)
    async with engine.begin() as connection:
        await connection.run_sync(Base.metadata.create_all)
    factory = async_sessionmaker(engine, expire_on_commit=False)
    async with factory() as session:
        await session.execute(delete(OutboxMessageModel))
        await session.execute(delete(FileAttachmentModel))
        await session.commit()
    yield factory
    await engine.dispose()


async def test_repository_keyset_pagination_and_outbox(session_factory) -> None:
    chat_id = uuid.uuid4()
    now = datetime(2026, 1, 1, tzinfo=UTC)
    created = []
    for index in range(4):
        attachment = make_attachment(chat_id, now + timedelta(minutes=index))
        created.append(attachment.id)
        async with SqlAlchemyUnitOfWork(session_factory) as uow:
            await uow.attachments.add(attachment)
            if index == 0:
                await uow.outbox.add(
                    FileUploaded(
                        file_id=attachment.id,
                        chat_id=chat_id,
                        uploader_id=attachment.uploader_id,
                        file_name=attachment.file_name,
                        content_type=attachment.content_type,
                        kind=attachment.kind.value,
                        size_bytes=attachment.size_bytes,
                    )
                )
            await uow.commit()

    async with SqlAlchemyUnitOfWork(session_factory) as uow:
        page = await uow.attachments.list_by_chat(chat_id, limit=3)
        assert [row.id for row in page] == [created[3], created[2], created[1]]
        before = (page[-1].created_at, page[-1].id)
        next_page = await uow.attachments.list_by_chat(chat_id, limit=3, before=before)
        assert [row.id for row in next_page] == [created[0]]

    async with SqlAlchemyUnitOfWork(session_factory) as uow:
        messages = await uow.outbox.claim_batch(10)
        assert [message.event_type for message in messages] == ["file.uploaded"]
        await uow.outbox.mark_published(messages[0].id, now)
        await uow.commit()

    async with SqlAlchemyUnitOfWork(session_factory) as uow:
        assert await uow.outbox.claim_batch(10) == []


def _storage(minio_container) -> tuple[MinioObjectStorage, str]:
    config = minio_container.get_config()
    endpoint = f"http://{config['endpoint']}"
    storage = MinioObjectStorage(
        endpoint=endpoint,
        public_endpoint=endpoint,
        access_key=config["access_key"],
        secret_key=config["secret_key"],
        bucket="chat-files",
        secure=False,
    )
    return storage, endpoint


async def test_presigned_put_get_roundtrip(minio_container) -> None:
    storage, _ = _storage(minio_container)
    await storage.ensure_bucket()
    key = f"tests/presigned-{uuid.uuid4()}.txt"
    content = b"presigned upload payload"
    try:
        put_url = await storage.presigned_put(
            key, content_type="text/plain", ttl_seconds=60
        )
        async with httpx.AsyncClient() as client:
            uploaded = await client.put(
                put_url,
                content=content,
                headers={"Content-Type": "text/plain"},
            )
            assert uploaded.status_code == 200

            get_url = await storage.presigned_get(key, ttl_seconds=60)
            downloaded = await client.get(get_url)
            assert downloaded.status_code == 200
            assert downloaded.content == content
    finally:
        await storage.delete(key)


async def test_minio_roundtrip(minio_container) -> None:
    storage, _ = _storage(minio_container)
    await storage.ensure_bucket()
    key = f"tests/{uuid.uuid4()}.txt"
    content = b"integration test payload"
    try:
        stored = await storage.put_stream(
            key,
            collect_stream(content),
            content_type="text/plain",
            max_bytes=1024,
        )
        assert stored.size_bytes == len(content)
        assert stored.checksum_sha256 == hashlib.sha256(content).hexdigest()

        stat = await storage.stat(key)
        assert stat is not None
        assert stat.size_bytes == len(content)

        url = await storage.presigned_get(key, ttl_seconds=60)
        assert url.startswith("http")

        await storage.delete(key)
        assert await storage.stat(key) is None
    finally:
        await storage.delete(key)
