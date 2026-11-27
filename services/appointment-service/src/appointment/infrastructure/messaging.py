"""RabbitMQ publisher (topic exchange, persistent messages)."""

from __future__ import annotations

import asyncio
from contextlib import suppress

import aio_pika
from aio_pika import DeliveryMode, ExchangeType, Message
from aio_pika.abc import AbstractExchange, AbstractRobustConnection

from appointment.domain.events import DomainEvent
from appointment.infrastructure.logging import get_logger

logger = get_logger(__name__)

class BrokerUnavailable(RuntimeError):
    """Raised when the message broker cannot accept a message right now."""

class RabbitEventPublisher:
    def __init__(self, url: str, exchange_name: str) -> None:
        self._url = url
        self._exchange_name = exchange_name
        self._connection: AbstractRobustConnection | None = None
        self._exchange: AbstractExchange | None = None
        self._lock = asyncio.Lock()

    @property
    def connected(self) -> bool:
        return self._exchange is not None and self._connection is not None

    async def connect(self) -> None:
        async with self._lock:
            if self.connected:
                return
            try:
                self._connection = await aio_pika.connect_robust(self._url, timeout=5)
                channel = await self._connection.channel()
                self._exchange = await channel.declare_exchange(
                    self._exchange_name,
                    ExchangeType.TOPIC,
                    durable=True,
                )
            except Exception as exc:  
                await self._reset()
                logger.warning("rabbitmq_connect_failed", error=str(exc))
                raise BrokerUnavailable("RabbitMQ is unavailable") from exc

            logger.info("rabbitmq_connected", exchange=self._exchange_name)

    async def publish(self, event: DomainEvent) -> None:
        if not self.connected:
            await self.connect()
        exchange = self._exchange
        if exchange is None:  # pragma: no cover - connect() always sets it or raises
            raise BrokerUnavailable("RabbitMQ exchange is not initialised")

        envelope = event.envelope()
        message = Message(
            body=envelope.body(),
            headers=envelope.headers(),
            content_type="application/json",
            content_encoding="utf-8",
            delivery_mode=DeliveryMode.PERSISTENT,
            message_id=str(envelope.event_id),
            correlation_id=envelope.correlation_id,
            timestamp=envelope.occurred_at,
        )
        try:
            await exchange.publish(message, routing_key=envelope.event_type)
        except Exception as exc:
            logger.warning("rabbitmq_publish_failed", error=str(exc))
            await self._reset()
            raise BrokerUnavailable("failed to publish event to RabbitMQ") from exc

    async def close(self) -> None:
        await self._reset()

    async def _reset(self) -> None:
        connection, self._connection = self._connection, None
        self._exchange = None
        if connection is not None:
            with suppress(Exception):  
                await connection.close()
