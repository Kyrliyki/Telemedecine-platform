"""Публикация доменных событий в RabbitMQ (topic exchange)."""

from __future__ import annotations

import asyncio
import json
import logging

import aio_pika
from aio_pika import DeliveryMode, ExchangeType, Message
from aio_pika.abc import AbstractChannel, AbstractConnection, AbstractExchange

from video_service.domain.events import DomainEvent

logger = logging.getLogger("video_service.events")


class RabbitMQEventPublisher:
    """Публикует интеграционные события в durable topic exchange.

    События отправляются после commit'а БД. Для гарантированной доставки в
    production используйте Outbox Pattern: сохраняйте событие в БД в той же
    транзакции, а этот адаптер вызывайте из relay-процесса.
    """

    def __init__(self, *, url: str, exchange_name: str) -> None:
        self._url = url
        self._exchange_name = exchange_name
        self._connection: AbstractConnection | None = None
        self._channel: AbstractChannel | None = None
        self._exchange: AbstractExchange | None = None
        self._lock = asyncio.Lock()

    async def _ensure_exchange(self) -> AbstractExchange:
        async with self._lock:
            if self._connection is None or self._connection.is_closed or self._exchange is None:
                self._connection = await aio_pika.connect_robust(self._url)
                self._channel = await self._connection.channel()
                self._exchange = await self._channel.declare_exchange(
                    self._exchange_name,
                    ExchangeType.TOPIC,
                    durable=True,
                )
            return self._exchange

    async def publish(self, event: DomainEvent) -> None:
        exchange = await self._ensure_exchange()
        await exchange.publish(
            Message(
                body=json.dumps(event.to_payload(), ensure_ascii=False).encode("utf-8"),
                content_type="application/json",
                delivery_mode=DeliveryMode.PERSISTENT,
                message_id=str(event.event_id),
                timestamp=event.occurred_at,
                type=event.event_name,
            ),
            routing_key=event.event_name,
        )
        logger.info("event.published", extra={"event_name": event.event_name})

    async def aclose(self) -> None:
        if self._channel is not None and not self._channel.is_closed:
            await self._channel.close()
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()
