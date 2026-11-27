"""PostgreSQL repository for notifications."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

from sqlalchemy import func, select, text, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.ext.asyncio import AsyncSession

from notification_service.domain.entities import Notification, NotificationDraft
from notification_service.domain.enums import NotificationChannel, NotificationStatus
from notification_service.infrastructure.db.models import (
    NotificationContactModel,
    NotificationModel,
)


def _to_entity(model: NotificationModel) -> Notification:
    return Notification(
        id=model.id,
        user_id=model.user_id,
        kind=model.kind,
        channel=NotificationChannel(model.channel),
        title=model.title,
        body=model.body,
        payload=dict(model.payload or {}),
        status=NotificationStatus(model.status),
        attempts=model.attempts,
        dedup_key=model.dedup_key,
        recipient_email=model.recipient_email,
        correlation_id=model.correlation_id,
        error=model.error,
        next_attempt_at=model.next_attempt_at,
        processing_started_at=model.processing_started_at,
        created_at=model.created_at,
        sent_at=model.sent_at,
        read_at=model.read_at,
    )


class SqlAlchemyNotificationRepository:
    async def add_many(self, session: AsyncSession, drafts: list[NotificationDraft]) -> int:
        if not drafts:
            return 0

        now = datetime.now(UTC)
        rows: list[dict[str, Any]] = []
        for draft in drafts:
            rows.append(
                {
                    "id": uuid4(),
                    "user_id": draft.user_id,
                    "kind": draft.kind,
                    "channel": draft.channel.value,
                    "title": draft.title,
                    "body": draft.body,
                    "payload": draft.payload,
                    "status": NotificationStatus.PENDING.value,
                    "attempts": 0,
                    "dedup_key": draft.dedup_key,
                    "recipient_email": draft.recipient_email,
                    "correlation_id": draft.correlation_id,
                    "created_at": now,
                }
            )

        statement = (
            pg_insert(NotificationModel)
            .values(rows)
            .on_conflict_do_nothing(index_elements=["dedup_key"])
        )
        result = await session.execute(statement)
        return result.rowcount or 0

    async def claim_pending(
        self,
        session: AsyncSession,
        *,
        limit: int,
        max_attempts: int,
        now: datetime,
        processing_cutoff: datetime,
    ) -> list[Notification]:
        statement = text(
            """
            WITH candidates AS (
                SELECT id
                FROM notification
                WHERE status IN ('pending', 'failed')
                  AND attempts < :max_attempts
                  AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
                ORDER BY created_at ASC
                LIMIT :batch_size
                FOR UPDATE SKIP LOCKED
            )
            UPDATE notification n
            SET status = 'processing',
                attempts = n.attempts + 1,
                processing_started_at = :now,
                error = NULL
            FROM candidates c
            WHERE n.id = c.id
            RETURNING n.id, n.user_id, n.kind, n.channel, n.title, n.body,
                      n.payload, n.status, n.attempts, n.dedup_key,
                      n.recipient_email, n.correlation_id, n.error,
                      n.next_attempt_at, n.processing_started_at,
                      n.created_at, n.sent_at, n.read_at
            """
        )
        rows = await session.execute(
            statement,
            {
                "max_attempts": max_attempts,
                "now": now,
                "batch_size": limit,
            },
        )
        return [
            Notification(
                id=row.id,
                user_id=row.user_id,
                kind=row.kind,
                channel=NotificationChannel(row.channel),
                title=row.title,
                body=row.body,
                payload=dict(row.payload or {}),
                status=NotificationStatus(row.status),
                attempts=row.attempts,
                dedup_key=row.dedup_key,
                recipient_email=row.recipient_email,
                correlation_id=row.correlation_id,
                error=row.error,
                next_attempt_at=row.next_attempt_at,
                processing_started_at=row.processing_started_at,
                created_at=row.created_at,
                sent_at=row.sent_at,
                read_at=row.read_at,
            )
            for row in rows
        ]

    async def mark_sent(
        self,
        session: AsyncSession,
        notification_id: UUID,
        *,
        now: datetime,
    ) -> None:
        await session.execute(
            update(NotificationModel)
            .where(NotificationModel.id == notification_id)
            .values(
                status=NotificationStatus.SENT.value,
                sent_at=now,
                processing_started_at=None,
                error=None,
            )
        )

    async def redact(self, session: AsyncSession, notification_id: UUID) -> None:
        await session.execute(
            update(NotificationModel)
            .where(NotificationModel.id == notification_id)
            .values(body="Ссылка отправлена на email.", payload={})
        )

    async def recover_stuck(
        self,
        session: AsyncSession,
        *,
        now: datetime,
        cutoff: datetime,
        max_attempts: int,
    ) -> int:
        result = await session.execute(
            text(
                """
                UPDATE notification
                SET status = CASE WHEN attempts >= :max_attempts THEN 'dead' ELSE 'failed' END,
                    error = 'delivery processing timeout',
                    processing_started_at = NULL,
                    next_attempt_at = :now
                WHERE status = 'processing'
                  AND processing_started_at <= :cutoff
                """
            ),
            {"max_attempts": max_attempts, "now": now, "cutoff": cutoff},
        )
        return result.rowcount or 0

    async def mark_failed(
        self,
        session: AsyncSession,
        notification_id: UUID,
        *,
        error: str,
        next_attempt_at: datetime | None,
        dead: bool,
    ) -> None:
        await session.execute(
            update(NotificationModel)
            .where(NotificationModel.id == notification_id)
            .values(
                status=(
                    NotificationStatus.DEAD.value if dead else NotificationStatus.FAILED.value
                ),
                error=error,
                next_attempt_at=next_attempt_at,
                processing_started_at=None,
            )
        )

    async def list_for_user(
        self,
        session: AsyncSession,
        *,
        user_id: str,
        unread_only: bool,
        limit: int,
        offset: int,
    ) -> tuple[list[Notification], int]:
        conditions = [
            NotificationModel.user_id == user_id,
            NotificationModel.channel == NotificationChannel.IN_APP.value,
        ]
        if unread_only:
            conditions.append(NotificationModel.read_at.is_(None))

        items_result = await session.execute(
            select(NotificationModel)
            .where(*conditions)
            .order_by(NotificationModel.created_at.desc())
            .limit(limit)
            .offset(offset)
        )
        total_result = await session.execute(
            select(func.count()).select_from(NotificationModel).where(*conditions)
        )
        return [_to_entity(model) for model in items_result.scalars()], total_result.scalar_one()

    async def get_for_user(
        self,
        session: AsyncSession,
        *,
        notification_id: UUID,
        user_id: str,
    ) -> Notification | None:
        result = await session.execute(
            select(NotificationModel).where(
                NotificationModel.id == notification_id,
                NotificationModel.user_id == user_id,
                NotificationModel.channel == NotificationChannel.IN_APP.value,
            )
        )
        model = result.scalar_one_or_none()
        return _to_entity(model) if model else None

    async def unread_count(self, session: AsyncSession, *, user_id: str) -> int:
        result = await session.execute(
            select(func.count())
            .select_from(NotificationModel)
            .where(
                NotificationModel.user_id == user_id,
                NotificationModel.channel == NotificationChannel.IN_APP.value,
                NotificationModel.read_at.is_(None),
            )
        )
        return result.scalar_one()

    async def mark_read(
        self,
        session: AsyncSession,
        *,
        notification_id: UUID,
        user_id: str,
        now: datetime,
    ) -> bool:
        result = await session.execute(
            update(NotificationModel)
            .where(
                NotificationModel.id == notification_id,
                NotificationModel.user_id == user_id,
                NotificationModel.channel == NotificationChannel.IN_APP.value,
            )
            .values(read_at=now)
        )
        return bool(result.rowcount)

    async def mark_all_read(self, session: AsyncSession, *, user_id: str, now: datetime) -> int:
        result = await session.execute(
            update(NotificationModel)
            .where(
                NotificationModel.user_id == user_id,
                NotificationModel.channel == NotificationChannel.IN_APP.value,
                NotificationModel.read_at.is_(None),
            )
            .values(read_at=now)
        )
        return result.rowcount or 0

    async def upsert_contact(
        self,
        session: AsyncSession,
        *,
        user_id: str,
        email: str,
        now: datetime,
    ) -> None:
        statement = pg_insert(NotificationContactModel).values(
            user_id=user_id,
            email=email,
            updated_at=now,
        )
        statement = statement.on_conflict_do_update(
            index_elements=["user_id"],
            set_={"email": email, "updated_at": now},
        )
        await session.execute(statement)

    async def get_contacts(
        self,
        session: AsyncSession,
        *,
        user_ids: set[str],
    ) -> dict[str, str]:
        if not user_ids:
            return {}
        result = await session.execute(
            select(NotificationContactModel.user_id, NotificationContactModel.email).where(
                NotificationContactModel.user_id.in_(user_ids)
            )
        )
        return {user_id: email for user_id, email in result.all()}
