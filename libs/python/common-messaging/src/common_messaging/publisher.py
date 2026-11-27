from __future__ import annotations

import asyncio
import logging
from typing import Protocol

import aio_pika
from aio_pika import DeliveryMode, ExchangeType, Message
from aio_pika.abc import AbstractChannel, AbstractConnection, AbstractExchange
from common_events import EventEnvelope

from .settings import RabbitMQSettings

logger = logging.getLogger(__name__)


class EventPublisher(Protocol):
    async def publish(self, envelope: EventEnvelope) -> None:
        ...


class RabbitEventPublisher:
    """Publishes events using the same body/headers as the Java publisher."""

    def __init__(self, settings: RabbitMQSettings) -> None:
        self._settings = settings
        self._connection: AbstractConnection | None = None
        self._channel: AbstractChannel | None = None
        self._exchange: AbstractExchange | None = None
        self._lock = asyncio.Lock()

    async def _ensure_connected(self) -> None:
        if self._exchange is not None:
            return
        async with self._lock:
            if self._exchange is not None:
                return
            self._connection = await aio_pika.connect_robust(self._settings.url)
            self._channel = await self._connection.channel(
                publisher_confirms=self._settings.publisher_confirms,
            )
            self._exchange = await self._channel.declare_exchange(
                self._settings.exchange,
                ExchangeType.TOPIC,
                durable=True,
            )
            logger.info(
                "RabbitMQ publisher connected: exchange=%s",
                self._settings.exchange,
            )

    async def publish(self, envelope: EventEnvelope) -> None:
        await self._ensure_connected()
        assert self._exchange is not None

        message = Message(
            body=envelope.body(),
            headers=envelope.headers(),
            content_type="application/json",
            content_encoding="utf-8",
            message_id=str(envelope.event_id),
            correlation_id=envelope.correlation_id,
            delivery_mode=DeliveryMode.PERSISTENT,
        )

        await self._exchange.publish(
            message,
            routing_key=envelope.event_type,
            mandatory=self._settings.mandatory,
            timeout=self._settings.confirm_timeout,
        )
        logger.debug(
            "Published event=%s type=%s",
            envelope.event_id,
            envelope.event_type,
        )

    async def close(self) -> None:
        self._exchange = None
        self._channel = None
        if self._connection is not None:
            await self._connection.close()
            self._connection = None

    async def __aenter__(self) -> RabbitEventPublisher:
        await self._ensure_connected()
        return self

    async def __aexit__(self, exc_type, exc, tb) -> None:
        await self.close()
