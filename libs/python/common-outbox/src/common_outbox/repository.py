from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from uuid import UUID

from common_events import EventEnvelope, utcnow
from sqlalchemy import bindparam, text
from sqlalchemy.ext.asyncio import AsyncSession

from .model import OutboxEvent, OutboxStatus


@dataclass(slots=True)
class ClaimedEvent:
    id: UUID
    event_id: UUID
    event_type: str
    payload: str
    correlation_id: str | None
    retry_count: int


class OutboxRepository:
    async def save(
        self,
        session: AsyncSession,
        envelope: EventEnvelope,
        service: str,
    ) -> OutboxEvent:
        event = OutboxEvent(
            event_id=envelope.event_id,
            event_type=envelope.event_type,
            service=service,
            payload=envelope.payload_json(),
            correlation_id=envelope.correlation_id,
            status=OutboxStatus.PENDING,
            created_at=envelope.occurred_at,
            retry_count=0,
        )
        session.add(event)
        await session.flush()
        return event

    async def claim_pending(
        self,
        session: AsyncSession,
        service: str,
        batch_size: int,
        max_attempts: int,
        now: datetime,
        processing_cutoff: datetime,
    ) -> list[ClaimedEvent]:
        statement = text(
            """
            WITH candidates AS (
                SELECT id, status, retry_count
                FROM outbox_event
                WHERE service = :service
                  AND (
                     (status IN :statuses
                      AND retry_count < :max_attempts
                      AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                    OR
                      (status = 'PROCESSING'
                       AND processing_started_at <= :cutoff
                       AND retry_count + 1 < :max_attempts)
                  )
                ORDER BY created_at ASC
                LIMIT :batch_size
                FOR UPDATE SKIP LOCKED
            )
            UPDATE outbox_event e
            SET status = 'PROCESSING',
                processing_started_at = :now,
                retry_count = CASE
                    WHEN c.status = 'PROCESSING' THEN e.retry_count + 1
                    ELSE e.retry_count
                END,
                last_error = CASE
                    WHEN c.status = 'PROCESSING' THEN 'Processing timeout, re-claimed'
                    ELSE e.last_error
                END
            FROM candidates c
            WHERE e.id = c.id
            RETURNING e.id, e.event_id, e.event_type, e.payload,
                      e.correlation_id, e.retry_count
            """
        ).bindparams(bindparam("statuses", expanding=True))

        rows = await session.execute(
            statement,
            {
                "service": service,
                "statuses": [OutboxStatus.PENDING.name, OutboxStatus.FAILED.name],
                "max_attempts": max_attempts,
                "now": now,
                "cutoff": processing_cutoff,
                "batch_size": batch_size,
            },
        )
        return [
            ClaimedEvent(
                id=row.id,
                event_id=row.event_id,
                event_type=row.event_type,
                payload=row.payload,
                correlation_id=row.correlation_id,
                retry_count=row.retry_count,
            )
            for row in rows
        ]

    async def mark_sent(self, session: AsyncSession, event_id: UUID) -> None:
        await session.execute(
            text(
                """
                UPDATE outbox_event
                SET status = 'SENT', sent_at = :now, processing_started_at = NULL
                WHERE event_id = :event_id AND status = 'PROCESSING'
                """
            ),
            {"event_id": event_id, "now": utcnow()},
        )

    async def mark_failed(
        self,
        session: AsyncSession,
        event_id: UUID,
        reason: str,
        next_attempt_at: datetime | None,
        dead: bool,
    ) -> None:
        await session.execute(
            text(
                """
                UPDATE outbox_event
                SET status = :status,
                    retry_count = retry_count + 1,
                    last_error = :reason,
                    next_attempt_at = :next_attempt_at,
                    processing_started_at = NULL
                WHERE event_id = :event_id AND status = 'PROCESSING'
                """
            ),
            {
                "event_id": event_id,
                "status": OutboxStatus.DEAD.name if dead else OutboxStatus.FAILED.name,
                "reason": reason[:1000] if reason else None,
                "next_attempt_at": next_attempt_at,
            },
        )

    async def mark_dead_stuck(
        self,
        session: AsyncSession,
        service: str,
        processing_cutoff: datetime,
        max_attempts: int,
    ) -> int:
        result = await session.execute(
            text(
                """
                UPDATE outbox_event
                SET status = 'DEAD',
                    last_error = 'Processing timeout, max attempts exhausted',
                    processing_started_at = NULL
                WHERE service = :service
                  AND status = 'PROCESSING'
                  AND processing_started_at <= :cutoff
                  AND retry_count + 1 >= :max_attempts
                """
            ),
            {
                "service": service,
                "cutoff": processing_cutoff,
                "max_attempts": max_attempts,
            },
        )
        return result.rowcount or 0

    async def delete_before(
        self,
        session: AsyncSession,
        service: str,
        status: OutboxStatus,
        cutoff: datetime,
    ) -> int:
        result = await session.execute(
            text(
                """
                DELETE FROM outbox_event
                WHERE service = :service
                  AND status = :status
                  AND created_at < :cutoff
                """
            ),
            {"service": service, "status": status.name, "cutoff": cutoff},
        )
        return result.rowcount or 0
