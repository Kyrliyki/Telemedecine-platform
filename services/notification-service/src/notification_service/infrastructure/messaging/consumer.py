"""RabbitMQ consumer that accepts canonical and legacy event shapes.

The platform is migrating to the canonical ``common-events`` envelope
(``eventType``/``eventId`` headers, payload body). The current Python services
still publish legacy events (``type``/``message_id`` headers, body containing
``event_type``/``event_name`` and payload). NotificationService must consume
both so the system works before and after the migration.
"""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
from collections.abc import Awaitable, Callable
from datetime import UTC, datetime
from typing import Any
from uuid import NAMESPACE_URL, UUID, uuid5

import aio_pika
from aio_pika import ExchangeType
from aio_pika.abc import AbstractConnection, AbstractQueue
from common_events import EventEnvelope
from common_messaging import ProcessedEventStore, handle_once

logger = logging.getLogger(__name__)

EventHandler = Callable[[EventEnvelope], Awaitable[None]]

def _header(headers: dict[str, Any], name: str) -> str | None:
    value = headers.get(name)
    if value is None:
        value = headers.get(name.encode())
    if value is None:
        return None
    return value.decode("utf-8") if isinstance(value, bytes) else str(value)

def _parse_datetime(value: Any) -> datetime:
    if isinstance(value, datetime):
        return value
    if isinstance(value, str):
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            pass
    return datetime.now(UTC)

def parse_message(message: Any) -> EventEnvelope | None:
    """Parse a canonical or legacy AMQP message into the common envelope."""

    headers = dict(message.headers or {})
    body = message.body or b""

    if _header(headers, "eventType"):
        return EventEnvelope.from_message(headers, body)

    try:
        data = json.loads(body.decode("utf-8")) if body else {}
    except (UnicodeDecodeError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict):
        return None

    event_type = (
        _header(headers, "type")
        or data.get("event_type")
        or data.get("event_name")
    )
    if not event_type:
        return None

    event_id_raw = _header(headers, "message_id") or data.get("event_id")
    event_id: UUID
    try:
        event_id = UUID(str(event_id_raw))
    except (TypeError, ValueError):
        
        event_id = uuid5(NAMESPACE_URL, f"{event_type}:{body!r}")

    payload = data.get("payload")
    if not isinstance(payload, dict):
        
        
        data_field = data.get("data")
        if isinstance(data_field, dict):
            payload = data_field
        else:
            payload = {
                key: value
                for key, value in data.items()
                if key not in {
                    "event_id",
                    "event_type",
                    "event_name",
                    "occurred_at",
                    "version",
                }
            }

    return EventEnvelope(
        event_id=event_id,
        event_type=str(event_type),
        occurred_at=_parse_datetime(data.get("occurred_at")),
        correlation_id=(
            _header(headers, "X-Correlation-Id")
            or _header(headers, "correlation_id")
            or data.get("correlation_id")
        ),
        payload=payload,
        version=int(data.get("version") or 1),
    )

class HybridRabbitConsumer:
    def __init__(
        self,
        *,
        url: str,
        exchange: str,
        queue: str,
        routing_key: str,
        service_name: str,
        store: ProcessedEventStore | None = None,
        prefetch: int = 10,
        dlx: str | None = None,
    ) -> None:
        self._url = url
        self._exchange_name = exchange
        self._queue_name = queue
        self._routing_key = routing_key
        self._service_name = service_name
        self._store = store
        self._prefetch = prefetch
        self._dlx_name = dlx or f"{exchange}.dlx"
        self._task: asyncio.Task[None] | None = None
        self._connection: AbstractConnection | None = None
        self._stopping = asyncio.Event()

    def is_healthy(self) -> bool:
        return self._connection is not None and not self._connection.is_closed

    async def start(self, handler: EventHandler) -> None:
        if self._task is not None:
            raise RuntimeError("consumer already started")
        self._stopping.clear()
        self._task = asyncio.create_task(
            self._run(handler),
            name=f"notification-consumer-{self._exchange_name}",
        )

    async def stop(self) -> None:
        self._stopping.set()
        if self._task is not None:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
            self._task = None
        if self._connection is not None:
            await self._connection.close()
            self._connection = None

    async def _run(self, handler: EventHandler) -> None:
        while not self._stopping.is_set():
            connection = None
            try:
                connection = await aio_pika.connect_robust(self._url)
                self._connection = connection
                channel = await connection.channel()
                await channel.set_qos(prefetch_count=self._prefetch)

                exchange = await channel.declare_exchange(
                    self._exchange_name,
                    ExchangeType.TOPIC,
                    durable=True,
                )
                dlx = await channel.declare_exchange(
                    self._dlx_name,
                    ExchangeType.TOPIC,
                    durable=True,
                )
                dlq_name = f"{self._queue_name}.dlq"
                queue: AbstractQueue = await channel.declare_queue(
                    self._queue_name,
                    durable=True,
                    arguments={
                        "x-dead-letter-exchange": self._dlx_name,
                        "x-dead-letter-routing-key": dlq_name,
                    },
                )
                dlq = await channel.declare_queue(dlq_name, durable=True)
                await dlq.bind(dlx, routing_key=dlq_name)
                await queue.bind(exchange, routing_key=self._routing_key)

                logger.info(
                    "notification.consumer_started queue=%s exchange=%s routing_key=%s",
                    self._queue_name,
                    self._exchange_name,
                    self._routing_key,
                )

                async with queue.iterator() as iterator:
                    async for message in iterator:
                        await self._process(message, handler)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception(
                    "notification.consumer_failed exchange=%s; reconnecting in 5s",
                    self._exchange_name,
                )
                await asyncio.sleep(5)
            finally:
                self._connection = None
                if connection is not None and not connection.is_closed:
                    await connection.close()

    async def _process(self, message: Any, handler: EventHandler) -> None:
        try:
            envelope = parse_message(message)
            if envelope is None:
                logger.warning("notification.consumer_unparseable_message")
                await message.ack()
                return
            if self._store is None:
                await handler(envelope)
            else:
                await handle_once(handler, envelope, self._store)
            await message.ack()
        except Exception:
            logger.exception(
                "notification.consumer_handler_failed exchange=%s message_id=%s",
                self._exchange_name,
                getattr(message, "message_id", None),
            )
            await message.nack(requeue=False)
