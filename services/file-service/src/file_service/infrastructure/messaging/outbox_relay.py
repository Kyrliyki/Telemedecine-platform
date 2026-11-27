"""Transactional outbox relay: PostgreSQL -> RabbitMQ."""

from __future__ import annotations

import asyncio
import contextlib
import logging

from file_service.application.ports import Clock, UnitOfWorkFactory
from file_service.infrastructure.messaging.rabbit import RabbitEventPublisher

logger = logging.getLogger(__name__)


class OutboxRelay:
    def __init__(
        self,
        *,
        uow_factory: UnitOfWorkFactory,
        publisher: RabbitEventPublisher,
        clock: Clock,
        poll_interval_seconds: float = 1.0,
        batch_size: int = 50,
    ) -> None:
        self._uow_factory = uow_factory
        self._publisher = publisher
        self._clock = clock
        self._poll_interval = poll_interval_seconds
        self._batch_size = batch_size
        self._task: asyncio.Task[None] | None = None
        self._stopping = asyncio.Event()

    async def start(self) -> None:
        if self._task is not None:
            return
        self._stopping.clear()
        self._task = asyncio.create_task(self._run(), name="outbox-relay")

    async def stop(self) -> None:
        self._stopping.set()
        if self._task is not None:
            self._task.cancel()
            with contextlib.suppress(asyncio.CancelledError):
                await self._task
            self._task = None
        await self._publisher.close()

    async def _run(self) -> None:
        while not self._stopping.is_set():
            try:
                processed, failed = await self.run_once()
                if failed:
                    await asyncio.sleep(min(2**failed, 30))
                elif processed == 0:
                    await asyncio.sleep(self._poll_interval)
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("outbox relay iteration failed")
                await asyncio.sleep(2)

    async def run_once(self) -> tuple[int, int]:
        """Publish one batch. Returns (processed, failure_count)."""
        processed = 0
        failures = 0
        async with self._uow_factory() as uow:
            messages = await uow.outbox.claim_batch(self._batch_size)
            for message in messages:
                try:
                    await self._publisher.publish(message.event_type, message.envelope)
                except Exception as exc:
                    failures += 1
                    await uow.outbox.record_failure(message.id, str(exc), self._clock.now())
                    logger.warning(
                        "failed to publish outbox message",
                        extra={"message_id": str(message.id), "event_type": message.event_type},
                    )
                    break
                await uow.outbox.mark_published(message.id, self._clock.now())
                processed += 1
            await uow.commit()
        if processed:
            logger.info("published %s outbox messages", processed)
        return processed, failures
