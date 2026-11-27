"""SQLAlchemy repository adapters."""

from __future__ import annotations

from datetime import datetime, timedelta
from uuid import UUID

from sqlalchemy import func, or_, select, tuple_
from sqlalchemy.ext.asyncio import AsyncSession

from file_service.application.ports import OutboxMessage
from file_service.domain.entities import Attachment
from file_service.domain.enums import FileStatus
from file_service.domain.events import DomainEvent
from file_service.infrastructure.db.mappers import to_entity, to_model
from file_service.infrastructure.db.models import FileAttachmentModel, OutboxMessageModel

MAX_OUTBOX_ATTEMPTS = 10


class SqlAttachmentRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add(self, attachment: Attachment) -> None:
        self._session.add(to_model(attachment))

    async def get(self, file_id: UUID, *, for_update: bool = False) -> Attachment | None:
        row = await self._session.get(FileAttachmentModel, file_id, with_for_update=for_update)
        return to_entity(row) if row is not None else None

    async def update(self, attachment: Attachment) -> None:
        await self._session.merge(to_model(attachment))

    async def list_by_chat(
        self,
        chat_id: UUID,
        *,
        limit: int,
        before: tuple[datetime, UUID] | None = None,
    ) -> list[Attachment]:
        stmt = (
            select(FileAttachmentModel)
            .where(
                FileAttachmentModel.chat_id == chat_id,
                FileAttachmentModel.status == FileStatus.READY.value,
            )
            .order_by(FileAttachmentModel.created_at.desc(), FileAttachmentModel.id.desc())
            .limit(limit)
        )
        if before is not None:
            stmt = stmt.where(
                tuple_(FileAttachmentModel.created_at, FileAttachmentModel.id) < tuple_(*before)
            )
        rows = (await self._session.scalars(stmt)).all()
        return [to_entity(row) for row in rows]

    async def list_all_by_chat(self, chat_id: UUID) -> list[Attachment]:
        stmt = (
            select(FileAttachmentModel)
            .where(FileAttachmentModel.chat_id == chat_id)
            .order_by(FileAttachmentModel.created_at.asc())
        )
        rows = (await self._session.scalars(stmt)).all()
        return [to_entity(row) for row in rows]


class SqlOutboxRepository:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def add(self, event: DomainEvent) -> None:
        now = event.occurred_at
        self._session.add(
            OutboxMessageModel(
                id=event.event_id,
                event_type=event.event_type,
                body=event.envelope(),
                occurred_at=now,
                created_at=now,
                attempts=0,
            )
        )

    async def claim_batch(self, limit: int) -> list[OutboxMessage]:
        stmt = (
            select(OutboxMessageModel)
            .where(
                OutboxMessageModel.published_at.is_(None),
                OutboxMessageModel.attempts < MAX_OUTBOX_ATTEMPTS,
                or_(
                    OutboxMessageModel.next_attempt_at.is_(None),
                    OutboxMessageModel.next_attempt_at <= func.now(),
                ),
            )
            .order_by(OutboxMessageModel.created_at.asc())
            .limit(limit)
            .with_for_update(skip_locked=True)
        )
        rows = (await self._session.scalars(stmt)).all()
        return [
            OutboxMessage(
                id=row.id,
                event_type=row.event_type,
                envelope=dict(row.body),
                attempts=row.attempts,
            )
            for row in rows
        ]

    async def mark_published(self, message_id: UUID, published_at: datetime) -> None:
        row = await self._session.get(OutboxMessageModel, message_id)
        if row is not None:
            row.published_at = published_at
            row.last_error = None

    async def record_failure(self, message_id: UUID, error: str, now: datetime) -> None:
        row = await self._session.get(OutboxMessageModel, message_id)
        if row is None:
            return
        row.attempts += 1
        row.last_error = error[:2000]
        delay_seconds = min(2**row.attempts, 300)
        row.next_attempt_at = now + timedelta(seconds=delay_seconds)
