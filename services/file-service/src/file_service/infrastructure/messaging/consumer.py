"""Consumer for ChatService integration events."""

from __future__ import annotations

import asyncio
import contextlib
import json
import logging
from collections.abc import Awaitable, Callable
from typing import Any

import aio_pika
from aio_pika import ExchangeType, IncomingMessage

logger = logging.getLogger(__name__)

EventHandler = Callable[[dict[str, Any]], Awaitable[None]]


class ChatEventsConsumer:
    def __init__(
        self,
        *,
        url: str,
        exchange_name: str,
        queue_name: str,
        dead_letter_exchange: str,
        routing_keys: tuple[str, ...] = ("chat.deleted",),
        handler: EventHandler,
        prefetch: int = 10,
    ) -> None:
        self._url = url
        self._exchange_name = exchange_name
        self._queue_name = queue_name
        self._dead_letter_exchange = dead_letter_exchange
        self._routing_keys = routing_keys
        self._handler = handler
        self._prefetch = prefetch
        self._task: asyncio.Task[None] | None = None
        self._stopping = asyncio.Event()

    async def start(self) -> None:
        if self._task is not None:
            return
        self._stopping.clear()
        self._task = asyncio.create_task(self._run(), name="chat-events-consumer")

    async def stop(self) -> None:
        self._stopping.set()
        if self._task is not None:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
            self._task = None

    async def _run(self) -> None:
        while not self._stopping.is_set():
            connection = None
            try:
                connection = await aio_pika.connect_robust(self._url)
                channel = await connection.channel()
                await channel.set_qos(prefetch_count=self._prefetch)

                dead_letter = await channel.declare_exchange(
                    self._dead_letter_exchange, ExchangeType.TOPIC, durable=True
                )
                exchange = await channel.declare_exchange(
                    self._exchange_name, ExchangeType.TOPIC, durable=True
                )
                queue = await channel.declare_queue(
                    self._queue_name,
                    durable=True,
                    arguments={
                        "x-dead-letter-exchange": self._dead_letter_exchange,
                        "x-queue-type": "quorum",
                    },
                )
                dlq = await channel.declare_queue(
                    f"{self._queue_name}.dlq",
                    durable=True,
                    arguments={"x-queue-type": "quorum"},
                )
                await dlq.bind(dead_letter, routing_key="#")
                for routing_key in self._routing_keys:
                    await queue.bind(exchange, routing_key=routing_key)

                logger.info(
                    "consuming ChatService events",
                    extra={"queue": self._queue_name, "routing_keys": list(self._routing_keys)},
                )
                async with queue.iterator() as iterator:
                    async for message in iterator:
                        await self._process(message)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("chat events consumer failed, reconnecting in 5s")
                await asyncio.sleep(5)
            finally:
                if connection is not None and not connection.is_closed:
                    await connection.close()

    async def _process(self, message: IncomingMessage) -> None:
        try:
            body = json.loads(message.body)
            if not isinstance(body, dict):
                raise ValueError("Event body must be a JSON object")
            await self._handler(body)
        except Exception:
            logger.exception("failed to process chat event; routing to DLQ")
            await message.reject(requeue=False)
            return
        await message.ack()
