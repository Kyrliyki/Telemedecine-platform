from __future__ import annotations

import asyncio
import logging
from collections.abc import Awaitable, Callable

import aio_pika
from aio_pika import ExchangeType
from aio_pika.abc import AbstractConnection, AbstractQueue
from common_events import EventEnvelope

from .idempotency import ProcessedEventStore, handle_once
from .settings import RabbitMQSettings

logger = logging.getLogger(__name__)

EventHandler = Callable[[EventEnvelope], Awaitable[None]]


class RabbitEventConsumer:
    """Consumes integration events from a durable queue with a DLQ.

    A handler failure rejects the message without requeueing, so RabbitMQ moves
    it to ``<queue>.dlq`` via the dead-letter exchange.

    Pass ``store`` to deduplicate deliveries by ``eventId``. All services must
    do this because the outbox only guarantees at-least-once delivery.
    """

    def __init__(
        self,
        settings: RabbitMQSettings,
        service_name: str,
        *,
        store: ProcessedEventStore | None = None,
    ) -> None:
        self._settings = settings
        self._service_name = service_name
        self._store = store
        self._connection: AbstractConnection | None = None
        self._task: asyncio.Task[None] | None = None

    @property
    def queue_name(self) -> str:
        return self._settings.queue or f"{self._service_name}.events"

    async def start(self, handler: EventHandler) -> None:
        if self._task is not None:
            raise RuntimeError("Consumer is already running")
        self._task = asyncio.create_task(
            self._consume(handler),
            name=f"rabbit-consumer-{self._service_name}",
        )

    async def stop(self) -> None:
        if self._task is not None:
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
            self._task = None
        if self._connection is not None:
            await self._connection.close()
            self._connection = None

    async def _consume(self, handler: EventHandler) -> None:
        self._connection = await aio_pika.connect_robust(self._settings.url)
        channel = await self._connection.channel()
        await channel.set_qos(prefetch_count=self._settings.prefetch_count)

        exchange = await channel.declare_exchange(
            self._settings.exchange,
            ExchangeType.TOPIC,
            durable=True,
        )
        dlx_name = self._settings.dlx or f"{self._settings.exchange}.dlx"
        dlx = await channel.declare_exchange(
            dlx_name,
            ExchangeType.TOPIC,
            durable=True,
        )

        queue_name = self.queue_name
        dead_letter_queue_name = f"{queue_name}.dlq"
        dead_letter_routing_key = dead_letter_queue_name

        queue: AbstractQueue = await channel.declare_queue(
            queue_name,
            durable=True,
            arguments={
                "x-dead-letter-exchange": dlx_name,
                "x-dead-letter-routing-key": dead_letter_routing_key,
            },
        )
        dead_letter_queue = await channel.declare_queue(
            dead_letter_queue_name,
            durable=True,
        )
        await queue.bind(exchange, routing_key=self._settings.routing_key)
        await dead_letter_queue.bind(dlx, routing_key=dead_letter_routing_key)

        logger.info(
            "RabbitMQ consumer started: queue=%s exchange=%s routingKey=%s",
            queue_name,
            self._settings.exchange,
            self._settings.routing_key,
        )

        async with queue.iterator() as iterator:
            async for message in iterator:
                await self._handle(message, handler)

    async def _handle(self, message, handler: EventHandler) -> None:
        try:
            envelope = EventEnvelope.from_message(message.headers, message.body)
            if self._store is None:
                await handler(envelope)
            else:
                await handle_once(handler, envelope, self._store)
            await message.ack()
        except Exception:
            logger.exception(
                "Event handling failed; message moved to DLQ (message_id=%s)",
                message.message_id,
            )
            await message.nack(requeue=False)
