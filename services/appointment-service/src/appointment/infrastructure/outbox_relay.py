"""Background relay that drains the transactional outbox into RabbitMQ.

At-least-once delivery: the row is marked as published after the broker accepted
the message. If the process dies in between, the event is published again with the
same ``event_id`` so consumers can deduplicate.
"""

from __future__ import annotations

import asyncio
from contextlib import suppress
from datetime import UTC, datetime

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from appointment.application.ports import EventPublisher
from appointment.domain.events import DomainEvent
from appointment.infrastructure.db.models import OutboxMessageModel
from appointment.infrastructure.logging import get_logger
from appointment.infrastructure.messaging import BrokerUnavailable
from appointment.infrastructure.metrics import OUTBOX_FAILURES, OUTBOX_PUBLISHED

logger = get_logger(__name__)

class OutboxRelay:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        publisher: EventPublisher,
        *,
        interval_seconds: float = 2.0,
        batch_size: int = 100,
    ) -> None:
        self._session_factory = session_factory
        self._publisher = publisher
        self._interval = interval_seconds
        self._batch_size = batch_size
        self._task: asyncio.Task[None] | None = None

    async def start(self) -> None:
        if self._task is not None:
            return
        self._task = asyncio.create_task(self._run(), name="outbox-relay")
        logger.info("outbox_relay_started", interval=self._interval)

    async def stop(self) -> None:
        task, self._task = self._task, None
        if task is None:
            return
        task.cancel()
        with suppress(asyncio.CancelledError):
            await task
        logger.info("outbox_relay_stopped")

    async def _run(self) -> None:
        while True:
            try:
                published = await self.drain_once()
            except asyncio.CancelledError:
                raise
            except BrokerUnavailable:
                await asyncio.sleep(self._interval)
                continue
            except Exception:
                logger.exception("outbox_relay_failed")
                await asyncio.sleep(self._interval)
                continue
            if published < self._batch_size:
                await asyncio.sleep(self._interval)

    async def drain_once(self) -> int:
        """Publish one batch; returns the number of successfully published events."""
        published = 0
        async with self._session_factory() as session:
            statement = (
                select(OutboxMessageModel)
                .where(OutboxMessageModel.published_at.is_(None))
                .order_by(OutboxMessageModel.created_at.asc())
                .limit(self._batch_size)
            )
            if session.get_bind().dialect.name == "postgresql":
                
                statement = statement.with_for_update(skip_locked=True)

            messages = (await session.execute(statement)).scalars().all()
            if not messages:
                return 0

            for message in messages:
                event = DomainEvent(
                    event_id=message.id,
                    event_type=message.event_type,
                    aggregate_id=message.aggregate_id,
                    payload=message.payload,
                    occurred_at=message.occurred_at,
                )
                try:
                    await self._publisher.publish(event)
                except Exception as exc:
                    message.attempts += 1
                    message.last_error = str(exc)[:1000]
                    await session.commit()
                    OUTBOX_FAILURES.inc()
                    logger.warning(
                        "outbox_publish_failed",
                        event_id=str(message.id),
                        event_type=message.event_type,
                        attempts=message.attempts,
                        error=str(exc),
                    )
                    raise BrokerUnavailable("outbox publish failed") from exc

                message.attempts += 1
                message.last_error = None
                message.published_at = datetime.now(UTC)
                OUTBOX_PUBLISHED.labels(event_type=message.event_type).inc()
                published += 1

            await session.commit()
        return published
