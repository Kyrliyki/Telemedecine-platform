"""Persist notifications for one integration event."""

from __future__ import annotations

from dataclasses import replace
from datetime import UTC, datetime

from common_db import session_scope
from common_events import EventEnvelope
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from notification_service.application.mapping import (
    build_drafts,
    contact_from_event,
)
from notification_service.application.ports import NotificationRepository
from notification_service.config import Settings
from notification_service.domain.enums import NotificationChannel


class IngestEventUseCase:
    """Maps an event to drafts, resolves email addresses and stores the rows.

    The handler is idempotent at two levels: the consumer deduplicates by
    ``eventId`` and the notification table has a UNIQUE ``dedup_key``.
    """

    def __init__(
        self,
        *,
        session_factory: async_sessionmaker[AsyncSession],
        repository: NotificationRepository,
        settings: Settings,
    ) -> None:
        self._session_factory = session_factory
        self._repository = repository
        self._settings = settings

    async def __call__(self, envelope: EventEnvelope) -> int:
        now = datetime.now(UTC)

        contact = contact_from_event(envelope)
        drafts = build_drafts(
            envelope,
            frontend_url=self._settings.frontend_url,
            email_enabled=self._settings.email_enabled,
            in_app_enabled=self._settings.in_app_enabled,
        )
        if not drafts:
            return 0

        async with session_scope(self._session_factory) as session:
            if contact is not None:
                await self._repository.upsert_contact(
                    session,
                    user_id=contact.user_id,
                    email=contact.email,
                    now=now,
                )

            missing_email_users = {
                draft.user_id
                for draft in drafts
                if draft.channel is NotificationChannel.EMAIL and not draft.recipient_email
            }
            emails = (
                await self._repository.get_contacts(session, user_ids=missing_email_users)
                if missing_email_users
                else {}
            )

            resolved = [
                replace(
                    draft,
                    recipient_email=draft.recipient_email or emails.get(draft.user_id),
                )
                for draft in drafts
            ]
            deliverable = [
                draft
                for draft in resolved
                if draft.channel is not NotificationChannel.EMAIL or draft.recipient_email
            ]

            return await self._repository.add_many(session, deliverable)
