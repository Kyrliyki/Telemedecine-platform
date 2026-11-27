"""RabbitMQ publisher adapter."""

from __future__ import annotations

import json
import logging
from datetime import UTC, datetime
from typing import Any

import aio_pika
from aio_pika import DeliveryMode, ExchangeType, Message
from aio_pika.abc import AbstractExchange, AbstractRobustConnection

logger = logging.getLogger(__name__)


class RabbitEventPublisher:
    def __init__(self, *, url: str, exchange_name: str) -> None:
        self._url = url
        self._exchange_name = exchange_name
        self._connection: AbstractRobustConnection | None = None
        self._exchange: AbstractExchange | None = None

    @property
    def exchange_name(self) -> str:
        return self._exchange_name

    async def connect(self) -> None:
        if self._connection is not None and not self._connection.is_closed and self._exchange is not None:
            return
        self._connection = await aio_pika.connect_robust(self._url)
        channel = await self._connection.channel(publisher_confirms=True)
        self._exchange = await channel.declare_exchange(
            self._exchange_name,
            ExchangeType.TOPIC,
            durable=True,
        )
        logger.info("connected to RabbitMQ", extra={"exchange": self._exchange_name})

    async def publish(self, routing_key: str, envelope: dict[str, Any]) -> None:
        await self.connect()
        if self._exchange is None:  # pragma: no cover - defensive
            raise RuntimeError("RabbitMQ exchange is not initialized")
        message = Message(
            body=json.dumps(envelope, separators=(",", ":")).encode("utf-8"),
            content_type="application/json",
            content_encoding="utf-8",
            delivery_mode=DeliveryMode.PERSISTENT,
            message_id=str(envelope.get("event_id", "")),
            type=str(envelope.get("event_type", routing_key)),
            timestamp=datetime.now(UTC),
            headers={"producer": "file-service", "schema_version": envelope.get("version", 1)},
        )
        await self._exchange.publish(message, routing_key=routing_key)

    def healthy(self) -> bool:
        return self._connection is not None and not self._connection.is_closed

    async def close(self) -> None:
        if self._connection is not None and not self._connection.is_closed:
            await self._connection.close()
        self._connection = None
        self._exchange = None
