"""Service-local dead-letter queue administration.

The public operator API is aggregated by the API Gateway. This module mirrors
the Java ``common-messaging`` DLQ contract so Python services can participate in
the same ``/api/v1/admin/dlq`` API.
"""

from __future__ import annotations

import asyncio
import logging
from collections.abc import Iterable
from dataclasses import dataclass
from typing import Any

import aio_pika
from aio_pika import DeliveryMode, Message
from aio_pika.abc import AbstractChannel, AbstractConnection, AbstractQueue
from common_events import to_camel
from pydantic import BaseModel, ConfigDict

from .settings import RabbitMQSettings

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class DlqQueueDefinition:
    key: str
    queue: str
    exchange: str
    description: str = ""

    def __post_init__(self) -> None:
        if not self.key or not self.queue or not self.exchange:
            raise ValueError("key, queue and exchange are required")


class DlqQueueCatalog:
    def __init__(self, queues: Iterable[DlqQueueDefinition] = ()) -> None:
        self._queues = tuple(queues)

    @property
    def queues(self) -> tuple[DlqQueueDefinition, ...]:
        return self._queues

    def find(self, key: str) -> DlqQueueDefinition | None:
        normalized = key.lower()
        return next((item for item in self._queues if item.key.lower() == normalized), None)


class _CamelModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
    )


class DlqQueueView(_CamelModel):
    key: str
    queue: str
    exchange: str
    description: str
    messages: int
    available: bool


class DlqReplayResult(_CamelModel):
    queue: str
    replayed: int
    failed: int
    dry_run: bool
    candidates: int


class DlqPurgeResult(_CamelModel):
    queue: str
    purged: int


class DlqNotFoundError(LookupError):
    pass


class DlqUnavailableError(RuntimeError):
    pass


class DlqAdminService:
    """Lists, replays and purges RabbitMQ DLQs for one Python service."""

    def __init__(
        self,
        settings: RabbitMQSettings,
        catalog: DlqQueueCatalog,
        *,
        timeout: float | None = None,
    ) -> None:
        self._settings = settings
        self._catalog = catalog
        self._timeout = timeout or settings.confirm_timeout
        self._connection: AbstractConnection | None = None
        self._lock = asyncio.Lock()

    async def _ensure_connection(self) -> AbstractConnection:
        if self._connection is not None and not self._connection.is_closed:
            return self._connection
        async with self._lock:
            if self._connection is None or self._connection.is_closed:
                self._connection = await aio_pika.connect_robust(self._settings.url)
            return self._connection

    async def _channel(self) -> AbstractChannel:
        connection = await self._ensure_connection()
        return await connection.channel(
            publisher_confirms=self._settings.publisher_confirms,
        )

    async def close(self) -> None:
        if self._connection is not None:
            await self._connection.close()
            self._connection = None

    async def list(self) -> list[DlqQueueView]:
        views: list[DlqQueueView] = []
        for definition in self._catalog.queues:
            messages = await self._message_count(definition.queue)
            views.append(
                DlqQueueView(
                    key=definition.key,
                    queue=definition.queue,
                    exchange=definition.exchange,
                    description=definition.description,
                    messages=messages,
                    available=messages >= 0,
                )
            )
        return views

    async def replay(self, key: str, limit: int = 100, dry_run: bool = False) -> DlqReplayResult:
        definition = self._require(key)
        safe_limit = min(max(limit, 1), 1000)
        candidates = await self._message_count(definition.queue)
        if candidates == 0:
            return DlqReplayResult(
                queue=definition.key,
                replayed=0,
                failed=0,
                dry_run=dry_run,
                candidates=0,
            )
        if dry_run:
            return DlqReplayResult(
                queue=definition.key,
                replayed=0,
                failed=0,
                dry_run=True,
                candidates=candidates,
            )

        channel, queue = await self._open_queue(definition)
        replayed = 0
        failed = 0
        try:
            exchange = await channel.get_exchange(definition.exchange, ensure=False)
            for _ in range(safe_limit):
                message = await queue.get(no_ack=False, fail=False, timeout=self._timeout)
                if message is None:
                    break
                routing_key = _routing_key(message.headers)
                if not routing_key:
                    failed += 1
                    await message.nack(requeue=True)
                    logger.warning(
                        "DLQ message without eventType/routing key cannot be replayed queue=%s",
                        definition.queue,
                    )
                    break
                try:
                    await exchange.publish(
                        _replay_message(message),
                        routing_key=routing_key,
                        mandatory=True,
                        timeout=self._timeout,
                    )
                    await message.ack()
                    replayed += 1
                except Exception:
                    failed += 1
                    await message.nack(requeue=True)
                    logger.exception(
                        "Failed to replay DLQ message queue=%s routingKey=%s",
                        definition.queue,
                        routing_key,
                    )
                    break
        finally:
            await channel.close()
        logger.info(
            "DLQ replay finished: queue=%s replayed=%s failed=%s",
            definition.queue,
            replayed,
            failed,
        )
        return DlqReplayResult(
            queue=definition.key,
            replayed=replayed,
            failed=failed,
            dry_run=False,
            candidates=candidates,
        )

    async def purge(self, key: str) -> DlqPurgeResult:
        definition = self._require(key)
        channel, queue = await self._open_queue(definition)
        try:
            result = await queue.purge()
            purged = int(result.message_count or 0)
            logger.warning("DLQ purged: queue=%s messages=%s", definition.queue, purged)
            return DlqPurgeResult(queue=definition.key, purged=purged)
        finally:
            await channel.close()

    async def _message_count(self, queue_name: str) -> int:
        channel = await self._channel()
        try:
            queue = await channel.declare_queue(queue_name, passive=True)
            return int(queue.declaration_result.message_count or 0)
        except Exception:
            logger.debug("DLQ is not available queue=%s", queue_name, exc_info=True)
            return -1
        finally:
            await channel.close()

    async def _open_queue(
        self, definition: DlqQueueDefinition
    ) -> tuple[AbstractChannel, AbstractQueue]:
        channel = await self._channel()
        try:
            queue = await channel.declare_queue(definition.queue, passive=True)
            return channel, queue
        except Exception as exception:
            await channel.close()
            raise DlqUnavailableError(
                f"DLQ is not available: {definition.queue}"
            ) from exception

    def _require(self, key: str) -> DlqQueueDefinition:
        definition = self._catalog.find(key)
        if definition is None:
            raise DlqNotFoundError(f"Unknown DLQ: {key}")
        return definition


def _replay_message(message: Any) -> Message:
    return Message(
        body=message.body or b"",
        headers=_clean_headers(message.headers),
        content_type=message.content_type or "application/json",
        content_encoding=message.content_encoding,
        message_id=message.message_id,
        correlation_id=message.correlation_id,
        delivery_mode=DeliveryMode.PERSISTENT,
    )


def _clean_headers(headers: dict[str, Any] | None) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in (headers or {}).items():
        if key in {
            "x-death",
            "x-first-death-reason",
            "x-first-death-queue",
            "x-first-death-exchange",
            "x-last-death-reason",
            "x-last-death-queue",
            "x-last-death-exchange",
        }:
            continue
        result[str(key)] = value
    return result


def _routing_key(headers: dict[str, Any] | None) -> str | None:
    values = headers or {}
    event_type = values.get("eventType")
    if event_type:
        return str(event_type)
    deaths = values.get("x-death")
    if isinstance(deaths, list):
        for death in deaths:
            if not isinstance(death, dict):
                continue
            routing_keys = death.get("routing-keys")
            if isinstance(routing_keys, list) and routing_keys:
                value = routing_keys[0]
                if value:
                    return str(value)
    return None
