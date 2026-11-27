"""End-to-end RabbitMQ consumer test for notification-service."""

from __future__ import annotations

import asyncio
from uuid import uuid4

import aio_pika
import pytest
from aio_pika import ExchangeType, Message
from common_events import AuthEventTypes, EventEnvelope
from common_events.auth import UserRegisteredPayload
from common_messaging import EventRouter
from sqlalchemy import select

from notification_service.application.use_cases.ingest import IngestEventUseCase
from notification_service.config import Settings
from notification_service.infrastructure.db.models import NotificationModel
from notification_service.infrastructure.db.repositories import (
    SqlAlchemyNotificationRepository,
)
from notification_service.infrastructure.messaging.consumer import HybridRabbitConsumer
from notification_service.infrastructure.messaging.handlers import register_handlers

pytestmark = pytest.mark.integration

async def _publish(url: str, envelope: EventEnvelope) -> None:
    connection = await aio_pika.connect_robust(url)
    try:
        channel = await connection.channel()
        exchange = await channel.declare_exchange("auth.events", ExchangeType.TOPIC, durable=True)
        await exchange.publish(
            Message(
                body=envelope.body(),
                headers=envelope.headers(),
                content_type="application/json",
            ),
            routing_key=envelope.event_type,
        )
    finally:
        await connection.close()

async def _wait_for_notifications(session_factory, user_id: str, max_wait_seconds: float = 10.0):
    deadline = asyncio.get_running_loop().time() + max_wait_seconds
    while asyncio.get_running_loop().time() < deadline:
        async with session_factory() as session:
            rows = list(
                (
                    await session.execute(
                        select(NotificationModel).where(NotificationModel.user_id == user_id)
                    )
                ).scalars()
            )
        if rows:
            return rows
        await asyncio.sleep(0.2)
    return []

async def test_consumer_ingests_event_and_deduplicates_redelivery(
    session_factory, rabbitmq_url: str
) -> None:
    settings = Settings.model_validate(
        {
            "DB_URL": "postgresql+asyncpg://unused",
            "DB_SCHEMA": "",
            "RABBITMQ_URL": rabbitmq_url,
            "in_app_enabled": True,
            "email_enabled": False,
            "frontend_url": "http://frontend.test",
        }
    )
    ingest = IngestEventUseCase(
        session_factory=session_factory,
        repository=SqlAlchemyNotificationRepository(),
        settings=settings,
    )
    router = EventRouter()
    register_handlers(router, ingest)

    queue = f"notification-integration-{uuid4().hex}"
    consumer = HybridRabbitConsumer(
        url=rabbitmq_url,
        exchange="auth.events",
        queue=queue,
        routing_key="auth.user.#",
        service_name="notification-integration",
    )
    await consumer.start(router.dispatch)
    try:
        await asyncio.sleep(1.0)

        user_id = uuid4()
        envelope = EventEnvelope.from_payload(
            AuthEventTypes.USER_REGISTERED,
            UserRegisteredPayload(
                user_id=user_id,
                email="consumer@example.com",
                roles=["PATIENT"],
                email_verified=True,
                source="PASSWORD",
            ),
        )

        await _publish(rabbitmq_url, envelope)

        rows = await _wait_for_notifications(session_factory, str(user_id))
        assert len(rows) == 1
        assert rows[0].kind.startswith("auth.")
        assert rows[0].channel == "in_app"

        
        await _publish(rabbitmq_url, envelope)
        await asyncio.sleep(1.0)
        rows = await _wait_for_notifications(session_factory, str(user_id))
        assert len(rows) == 1
    finally:
        await consumer.stop()
