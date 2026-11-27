"""Persistence adapter for the transactional outbox."""

from __future__ import annotations

from datetime import UTC, datetime

from sqlalchemy.ext.asyncio import AsyncSession

from appointment.domain.events import DomainEvent
from appointment.infrastructure.db.models import OutboxMessageModel


class SqlAlchemyOutboxRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add(self, event: DomainEvent) -> None:
        self._session.add(
            OutboxMessageModel(
                id=event.event_id,
                event_type=event.event_type,
                routing_key=event.routing_key,
                aggregate_id=event.aggregate_id,
                payload=event.payload,
                occurred_at=event.occurred_at,
                created_at=datetime.now(UTC),
                attempts=0,
            )
        )
        await self._session.flush()
