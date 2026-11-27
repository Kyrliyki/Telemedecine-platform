"""RabbitMQ publisher for chat integration events."""

from __future__ import annotations

import json
import logging
from datetime import UTC, datetime
from uuid import UUID, uuid4

import aio_pika
from aio_pika import DeliveryMode, ExchangeType, Message
from aio_pika.abc import AbstractExchange, AbstractRobustConnection

logger = logging.getLogger(__name__)


class RabbitChatEventsPublisher:
    """Publishes legacy-shape ``chat.deleted`` events for file-service."""

    def __init__(self, *, url: str, exchange_name: str = "chat.events") -> None:
        self._url = url
        self._exchange_name = exchange_name
        self._connection: AbstractRobustConnection | None = None
        self._exchange: AbstractExchange | None = None

    async def _ensure_exchange(self) -> AbstractExchange:
        if self._exchange is not None and not self._exchange.channel.is_closed:
            return self._exchange
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()
        self._connection = await aio_pika.connect_robust(self._url, timeout=5)
        channel = await self._connection.channel()
        self._exchange = await channel.declare_exchange(
            self._exchange_name,
            ExchangeType.TOPIC,
            durable=True,
        )
        return self._exchange

    async def ping(self) -> bool:
        try:
            await self._ensure_exchange()
            return True
        except Exception:
            logger.warning("chat.rabbitmq.ping_failed")
            return False

    async def publish_deleted(self, *, chat_id: UUID, request_id: str) -> None:
        event_id = uuid4()
        occurred_at = datetime.now(UTC)
        body = {
            "event_id": str(event_id),
            "event_type": "chat.deleted",
            "occurred_at": occurred_at.isoformat(),
            "payload": {"chat_id": str(chat_id), "request_id": request_id},
        }
        try:
            exchange = await self._ensure_exchange()
            await exchange.publish(
                Message(
                    body=json.dumps(body, separators=(",", ":")).encode("utf-8"),
                    content_type="application/json",
                    delivery_mode=DeliveryMode.PERSISTENT,
                    message_id=str(event_id),
                    timestamp=occurred_at,
                    type="chat.deleted",
                ),
                routing_key="chat.deleted",
            )
        except Exception:
            logger.exception("chat.deleted.publish_failed", extra={"chat_id": str(chat_id)})

    async def aclose(self) -> None:
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()
        self._connection = None
        self._exchange = None
