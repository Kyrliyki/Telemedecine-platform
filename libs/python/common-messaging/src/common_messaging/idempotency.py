"""Consumer-side deduplication for at-least-once delivery.

The outbox relay publishes at least once. RabbitMQ redeliveries, retries and
DLQ replays can therefore produce duplicates, so every consumer needs an
idempotency guard keyed by ``eventId``.

The PostgreSQL store uses an ``INSERT ... ON CONFLICT DO NOTHING`` check, which
is safe for concurrent consumers of the same event.
"""

from __future__ import annotations

import logging
from collections.abc import Awaitable, Callable
from typing import Protocol
from uuid import UUID

from common_events import EventEnvelope, utcnow
from sqlalchemy import text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

logger = logging.getLogger(__name__)

EventHandler = Callable[[EventEnvelope], Awaitable[None]]


class ProcessedEventStore(Protocol):
    async def seen(self, event_id: UUID) -> bool:
        ...

    async def mark(self, event_id: UUID, event_type: str) -> None:
        ...


class InMemoryProcessedEventStore:
    """Single-process store for tests and local experiments.

    It is lost on restart and must not be used across replicas.
    """

    def __init__(self) -> None:
        self._seen: set[UUID] = set()

    async def seen(self, event_id: UUID) -> bool:
        return event_id in self._seen

    async def mark(self, event_id: UUID, event_type: str) -> None:
        self._seen.add(event_id)


class PostgresProcessedEventStore:
    """Durable store backed by ``processed_event`` in the service schema.

    The DDL lives in ``common-messaging/sql/processed_event.sql`` and is meant
    to be part of the owning service's migration.
    """

    def __init__(self, session_factory: async_sessionmaker[AsyncSession]) -> None:
        self._session_factory = session_factory

    async def seen(self, event_id: UUID) -> bool:
        async with self._session_factory() as session:
            result = await session.execute(
                text("SELECT 1 FROM processed_event WHERE event_id = :event_id"),
                {"event_id": event_id},
            )
            return result.first() is not None

    async def mark(self, event_id: UUID, event_type: str) -> None:
        async with self._session_factory() as session:
            await session.execute(
                text(
                    """
                    INSERT INTO processed_event (event_id, event_type, processed_at)
                    VALUES (:event_id, :event_type, :processed_at)
                    ON CONFLICT (event_id) DO NOTHING
                    """
                ),
                {
                    "event_id": event_id,
                    "event_type": event_type,
                    "processed_at": utcnow(),
                },
            )
            await session.commit()


async def handle_once(
    handler: EventHandler,
    envelope: EventEnvelope,
    store: ProcessedEventStore,
) -> bool:
    """Run ``handler`` unless the event was already processed.

    The event is marked **after** a successful handler call. If the handler
    fails, the message is not marked and RabbitMQ moves it to the DLQ instead
    of silently losing it.
    """

    if await store.seen(envelope.event_id):
        logger.info(
            "Skipping duplicate event id=%s type=%s",
            envelope.event_id,
            envelope.event_type,
        )
        return False

    await handler(envelope)
    await store.mark(envelope.event_id, envelope.event_type)
    return True


def idempotent(
    handler: EventHandler,
    store: ProcessedEventStore,
) -> EventHandler:
    """Return a handler wrapper that applies :func:`handle_once`."""

    async def wrapper(envelope: EventEnvelope) -> None:
        await handle_once(handler, envelope, store)

    return wrapper
