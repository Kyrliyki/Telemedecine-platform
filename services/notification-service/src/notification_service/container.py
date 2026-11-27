"""Composition root for NotificationService."""

from __future__ import annotations

import asyncio
import contextlib
import logging
from dataclasses import dataclass, field

from common_db import DatabaseSettings, create_engine, health_check, session_factory
from common_messaging import (
    EventRouter,
    PostgresProcessedEventStore,
    RabbitEventPublisher,
    RabbitMQSettings,
)
from common_outbox import OutboxRelay, OutboxSettings
from sqlalchemy.ext.asyncio import AsyncEngine, AsyncSession, async_sessionmaker

from notification_service.application.ports import EmailSender, InAppPublisher
from notification_service.application.use_cases.dispatch import NotificationDispatcher
from notification_service.application.use_cases.ingest import IngestEventUseCase
from notification_service.config import Settings
from notification_service.infrastructure.channels.email import SmtpEmailSender
from notification_service.infrastructure.channels.email_disabled import DisabledEmailSender
from notification_service.infrastructure.channels.in_app import RedisInAppPublisher
from notification_service.infrastructure.db.repositories import SqlAlchemyNotificationRepository
from notification_service.infrastructure.messaging.consumer import HybridRabbitConsumer
from notification_service.infrastructure.messaging.handlers import register_handlers

logger = logging.getLogger(__name__)

@dataclass
class Container:
    settings: Settings
    engine: AsyncEngine
    session_factory: async_sessionmaker[AsyncSession]
    repository: SqlAlchemyNotificationRepository
    ingest: IngestEventUseCase
    dispatcher: NotificationDispatcher
    publisher: RabbitEventPublisher
    relay: OutboxRelay
    in_app: InAppPublisher
    email: EmailSender
    router: EventRouter
    consumers: list[HybridRabbitConsumer] = field(default_factory=list)
    relay_task: asyncio.Task[None] | None = None
    dispatcher_task: asyncio.Task[None] | None = None
    started: bool = False
    managed: bool = True

    async def start(self) -> None:
        if self.started:
            return
        self.relay_task = asyncio.create_task(
            self.relay.run_forever(),
            name=f"{self.settings.service_name}-outbox-relay",
        )
        self.dispatcher_task = asyncio.create_task(
            self.dispatcher.run_forever(),
            name=f"{self.settings.service_name}-dispatcher",
        )
        for consumer in self.consumers:
            await consumer.start(self.router.dispatch)
        self.started = True

    async def stop(self) -> None:
        for consumer in self.consumers:
            await consumer.stop()
        if self.dispatcher_task is not None:
            self.dispatcher_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self.dispatcher_task
            self.dispatcher_task = None
        if self.relay_task is not None:
            self.relay_task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self.relay_task
            self.relay_task = None
        await self.in_app.close()
        await self.publisher.close()
        await self.engine.dispose()
        self.started = False

    async def check_database(self) -> bool:
        return await health_check(self.engine)

    async def check_redis(self) -> bool:
        return await self.in_app.ping()

    async def check_rabbitmq(self) -> bool:
        if not self.consumers:
            return True
        return all(consumer.is_healthy() for consumer in self.consumers)

def build_container(settings: Settings) -> Container:
    db_settings = DatabaseSettings.model_validate(
        {
            "DB_URL": settings.database_url,
            "DB_SCHEMA": settings.db_schema,
            "DB_POOL_SIZE": settings.db_pool_size,
            "DB_MAX_OVERFLOW": settings.db_max_overflow,
            "DB_ECHO": settings.db_echo,
        }
    )
    engine = create_engine(db_settings)
    factory = session_factory(engine)
    repository = SqlAlchemyNotificationRepository()

    in_app: InAppPublisher = RedisInAppPublisher(settings.redis_url)
    email: EmailSender = (
        SmtpEmailSender(settings) if settings.email_enabled else DisabledEmailSender()
    )

    ingest = IngestEventUseCase(
        session_factory=factory,
        repository=repository,
        settings=settings,
    )
    dispatcher = NotificationDispatcher(
        session_factory=factory,
        repository=repository,
        in_app=in_app,
        email=email,
        settings=settings,
    )

    publisher = RabbitEventPublisher(
        RabbitMQSettings.model_validate(
            {
                "RABBITMQ_URL": settings.rabbitmq_url,
                "exchange": settings.rabbitmq_notification_exchange,
                
                
                "mandatory": False,
            }
        )
    )
    relay = OutboxRelay(
        factory,
        publisher,
        OutboxSettings(),
        service_name=settings.service_name,
    )

    store = PostgresProcessedEventStore(factory)
    router = EventRouter()
    register_handlers(router, ingest)

    consumers = [
        HybridRabbitConsumer(
            url=settings.rabbitmq_url,
            exchange=settings.rabbitmq_auth_exchange,
            queue=settings.rabbitmq_auth_queue,
            routing_key="auth.user.#",
            service_name=settings.service_name,
            store=store,
            prefetch=settings.rabbitmq_prefetch_count,
        ),
        HybridRabbitConsumer(
            url=settings.rabbitmq_url,
            exchange=settings.rabbitmq_user_exchange,
            queue=settings.rabbitmq_user_queue,
            routing_key="user.#",
            service_name=settings.service_name,
            store=store,
            prefetch=settings.rabbitmq_prefetch_count,
        ),
        HybridRabbitConsumer(
            url=settings.rabbitmq_url,
            exchange=settings.rabbitmq_appointment_exchange,
            queue=settings.rabbitmq_appointment_queue,
            routing_key="appointment.#",
            service_name=settings.service_name,
            store=store,
            prefetch=settings.rabbitmq_prefetch_count,
        ),
        HybridRabbitConsumer(
            url=settings.rabbitmq_url,
            exchange=settings.rabbitmq_video_exchange,
            queue=settings.rabbitmq_video_queue,
            routing_key="video.#",
            service_name=settings.service_name,
            store=store,
            prefetch=settings.rabbitmq_prefetch_count,
        ),
    ]

    container = Container(
        settings=settings,
        engine=engine,
        session_factory=factory,
        repository=repository,
        ingest=ingest,
        dispatcher=dispatcher,
        publisher=publisher,
        relay=relay,
        in_app=in_app,
        email=email,
        router=router,
        consumers=consumers,
    )
    return container
