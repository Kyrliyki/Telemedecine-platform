"""Composition root: wires adapters, use cases and background workers."""

from __future__ import annotations

import asyncio
import contextlib
import logging
from typing import Any

from sqlalchemy import text
from sqlalchemy.ext.asyncio import async_sessionmaker, create_async_engine

from file_service.application.ports import ChatAccessPolicy
from file_service.application.use_cases import UseCases, build_use_cases
from file_service.application.use_cases.common import AppDeps
from file_service.domain.value_objects import UploadPolicy
from file_service.infrastructure.cache.redis_cache import RedisFileCache, RedisJsonCache
from file_service.infrastructure.chat_access import (
    AllowAllChatAccessPolicy,
    DenyAllChatAccessPolicy,
    HttpChatAccessPolicy,
)
from file_service.infrastructure.config import Settings
from file_service.infrastructure.db.uow import SqlAlchemyUnitOfWork
from file_service.infrastructure.messaging.consumer import ChatEventsConsumer
from file_service.infrastructure.messaging.handlers import ChatEventsHandler
from file_service.infrastructure.messaging.outbox_relay import OutboxRelay
from file_service.infrastructure.messaging.rabbit import RabbitEventPublisher
from file_service.infrastructure.security.jwt import JwtVerifier
from file_service.infrastructure.storage.minio_storage import MinioObjectStorage
from file_service.infrastructure.system import SystemClock, Uuid4Generator

logger = logging.getLogger(__name__)


class Container:
    def __init__(self, settings: Settings) -> None:
        self.settings = settings
        connect_args = (
            {"server_settings": {"search_path": settings.db_schema}}
            if settings.db_schema
            else {}
        )
        self.engine = create_async_engine(
            settings.database_url,
            echo=settings.db_echo,
            pool_pre_ping=True,
            pool_size=settings.db_pool_size,
            max_overflow=settings.db_pool_size,
            connect_args=connect_args,
        )
        self.session_factory = async_sessionmaker(self.engine, expire_on_commit=False)

        self.storage = MinioObjectStorage(
            endpoint=settings.s3_endpoint,
            public_endpoint=settings.s3_public_endpoint,
            access_key=settings.s3_access_key,
            secret_key=settings.s3_secret_key,
            bucket=settings.s3_bucket,
            region=settings.s3_region,
            secure=settings.s3_secure,
        )
        self.redis = RedisJsonCache(settings.redis_url)
        self.file_cache = RedisFileCache(self.redis)
        self.chat_access: ChatAccessPolicy = self._build_chat_access(settings)

        self.clock = SystemClock()
        self.ids = Uuid4Generator()
        self.policy = UploadPolicy(
            max_size_bytes=settings.max_file_size_bytes,
            allowed_content_types=settings.allowed_content_type_set,
        )
        self.deps = AppDeps(
            uow_factory=lambda: SqlAlchemyUnitOfWork(self.session_factory),
            storage=self.storage,
            cache=self.file_cache,
            clock=self.clock,
            ids=self.ids,
            policy=self.policy,
            chat_access=self.chat_access,
            bucket=settings.s3_bucket,
            presign_ttl_seconds=settings.s3_presign_ttl_seconds,
            cache_ttl_seconds=settings.file_cache_ttl_seconds,
        )
        self.use_cases: UseCases = build_use_cases(self.deps)
        self.verifier = JwtVerifier(settings)

        self.publisher = RabbitEventPublisher(
            url=settings.rabbitmq_url,
            exchange_name=settings.rabbitmq_events_exchange,
        )
        self.outbox_relay = OutboxRelay(
            uow_factory=self.deps.uow_factory,
            publisher=self.publisher,
            clock=self.clock,
        )
        self.chat_events_consumer = ChatEventsConsumer(
            url=settings.rabbitmq_url,
            exchange_name=settings.rabbitmq_chat_events_exchange,
            queue_name=settings.rabbitmq_consumer_queue,
            dead_letter_exchange=settings.rabbitmq_dead_letter_exchange,
            handler=ChatEventsHandler(self.use_cases.purge_chat_files),
        )
        self._bucket_task: asyncio.Task[None] | None = None
        self._started = False

    def _build_chat_access(self, settings: Settings) -> ChatAccessPolicy:
        if settings.chat_service_url:
            return HttpChatAccessPolicy(
                base_url=settings.chat_service_url,
                internal_token=settings.internal_api_token,
                cache=self.redis,
                cache_ttl_seconds=settings.chat_access_cache_ttl_seconds,
            )
        if settings.chat_access_allow_all:
            logger.warning("CHAT_SERVICE_URL is empty; chat membership checks are disabled")
            return AllowAllChatAccessPolicy()
        return DenyAllChatAccessPolicy()

    async def start(self) -> None:
        if self._started:
            return
        try:
            await asyncio.wait_for(self.storage.ensure_bucket(), timeout=15)
        except Exception:
            logger.exception("MinIO bucket initialization failed; retrying in the background")
            self._bucket_task = asyncio.create_task(self._ensure_bucket_forever(), name="minio-bucket-init")
        try:
            await asyncio.wait_for(self.publisher.connect(), timeout=10)
        except Exception:
            logger.exception("RabbitMQ connection failed; the outbox relay will retry")
        await self.outbox_relay.start()
        await self.chat_events_consumer.start()
        self._started = True

    async def _ensure_bucket_forever(self) -> None:
        while True:
            try:
                await self.storage.ensure_bucket()
                logger.info("MinIO bucket is ready")
                return
            except asyncio.CancelledError:
                raise
            except Exception as exc:
                logger.warning("MinIO bucket is not ready yet (%s), retrying in 5s", type(exc).__name__)
                await asyncio.sleep(5)

    async def stop(self) -> None:
        if not self._started:
            return
        if self._bucket_task is not None:
            self._bucket_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._bucket_task
            self._bucket_task = None
        await self.outbox_relay.stop()
        await self.chat_events_consumer.stop()
        await self.chat_access.aclose()
        await self.verifier.aclose()
        await self.redis.close()
        await self.engine.dispose()
        self._started = False

    async def health(self) -> dict[str, Any]:
        async def check_database() -> None:
            async with self.engine.connect() as connection:
                await connection.execute(text("SELECT 1"))

        async def guarded(check: Any) -> str:
            try:
                async with asyncio.timeout(3):
                    result = await check()
                return "ok" if result is not False else "error"
            except Exception as exc:
                logger.debug("health check failed", exc_info=True)
                return f"error: {type(exc).__name__}"

        database, storage, redis, rabbit = await asyncio.gather(
            guarded(check_database),
            guarded(self.storage.healthy),
            guarded(self.redis.ping),
            guarded(lambda: asyncio.sleep(0, result=self.publisher.healthy())),
        )
        components = {
            "database": database,
            "storage": storage,
            "redis": redis,
            "rabbitmq": rabbit,
        }
        overall = "ok" if all(value == "ok" for value in components.values()) else "degraded"
        return {"status": overall, "components": components}
