"""Application-layer ports (implemented by infrastructure)."""

from __future__ import annotations

from datetime import datetime
from typing import Protocol
from uuid import UUID

from sqlalchemy.ext.asyncio import AsyncSession

from notification_service.domain.entities import Notification, NotificationDraft


class NotificationRepository(Protocol):
    async def add_many(self, session: AsyncSession, drafts: list[NotificationDraft]) -> int: ...

    async def claim_pending(
        self,
        session: AsyncSession,
        *,
        limit: int,
        max_attempts: int,
        now: datetime,
        processing_cutoff: datetime,
    ) -> list[Notification]: ...

    async def mark_sent(
        self,
        session: AsyncSession,
        notification_id: UUID,
        *,
        now: datetime,
    ) -> None: ...

    async def redact(self, session: AsyncSession, notification_id: UUID) -> None: ...

    async def recover_stuck(
        self,
        session: AsyncSession,
        *,
        now: datetime,
        cutoff: datetime,
        max_attempts: int,
    ) -> int: ...

    async def mark_failed(
        self,
        session: AsyncSession,
        notification_id: UUID,
        *,
        error: str,
        next_attempt_at: datetime | None,
        dead: bool,
    ) -> None: ...

    async def list_for_user(
        self,
        session: AsyncSession,
        *,
        user_id: str,
        unread_only: bool,
        limit: int,
        offset: int,
    ) -> tuple[list[Notification], int]: ...

    async def get_for_user(
        self,
        session: AsyncSession,
        *,
        notification_id: UUID,
        user_id: str,
    ) -> Notification | None: ...

    async def unread_count(self, session: AsyncSession, *, user_id: str) -> int: ...

    async def mark_read(
        self,
        session: AsyncSession,
        *,
        notification_id: UUID,
        user_id: str,
        now: datetime,
    ) -> bool: ...

    async def mark_all_read(self, session: AsyncSession, *, user_id: str, now: datetime) -> int: ...

    async def upsert_contact(
        self,
        session: AsyncSession,
        *,
        user_id: str,
        email: str,
        now: datetime,
    ) -> None: ...

    async def get_contacts(
        self,
        session: AsyncSession,
        *,
        user_ids: set[str],
    ) -> dict[str, str]: ...


class InAppPublisher(Protocol):
    async def publish(self, *, user_id: str, message: dict) -> None: ...

    async def ping(self) -> bool: ...

    async def close(self) -> None: ...


class EmailSender(Protocol):
    async def send(self, notification: Notification) -> None: ...


class Clock(Protocol):
    def now(self) -> datetime: ...
