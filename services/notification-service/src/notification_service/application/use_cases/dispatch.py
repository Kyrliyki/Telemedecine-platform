"""Background delivery of pending notifications."""

from __future__ import annotations

import asyncio
import logging
from datetime import UTC, datetime, timedelta

from common_db import session_scope
from common_events import (
    EventEnvelope,
    NotificationEventTypes,
    NotificationSentPayload,
)
from common_outbox import OutboxRepository
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from notification_service.application.ports import (
    EmailSender,
    InAppPublisher,
    NotificationRepository,
)
from notification_service.config import Settings
from notification_service.domain.entities import Notification
from notification_service.domain.enums import NotificationChannel, NotificationKind

logger = logging.getLogger(__name__)


class NotificationDispatcher:
    """Claims pending notifications and delivers them at-least-once.

    Delivery status lives in PostgreSQL, so a crash between SMTP/Redis and the
    status update only causes a retry. The unique ``dedup_key`` prevents a
    second notification row; the consumer deduplicates events by ``eventId``.
    """

    def __init__(
        self,
        *,
        session_factory: async_sessionmaker[AsyncSession],
        repository: NotificationRepository,
        in_app: InAppPublisher,
        email: EmailSender,
        settings: Settings,
    ) -> None:
        self._session_factory = session_factory
        self._repository = repository
        self._in_app = in_app
        self._email = email
        self._settings = settings
        self._outbox = OutboxRepository()

    async def run_once(self) -> int:
        now = datetime.now(UTC)
        cutoff = now - timedelta(seconds=self._settings.dispatcher_processing_timeout_seconds)

        async with session_scope(self._session_factory) as session:
            await self._repository.recover_stuck(
                session,
                now=now,
                cutoff=cutoff,
                max_attempts=self._settings.dispatcher_max_attempts,
            )
            claimed = await self._repository.claim_pending(
                session,
                limit=self._settings.dispatcher_batch_size,
                max_attempts=self._settings.dispatcher_max_attempts,
                now=now,
                processing_cutoff=cutoff,
            )

        for notification in claimed:
            try:
                await self._deliver(notification)
            except Exception as exception:
                await self._mark_failure(notification, exception)
                continue

            await self._mark_success(notification)

        return len(claimed)

    async def _deliver(self, notification: Notification) -> None:
        if notification.channel is NotificationChannel.IN_APP:
            await self._in_app.publish(
                user_id=notification.user_id,
                message={
                    "type": "notification.created",
                    "payload": {
                        "id": str(notification.id),
                        "kind": notification.kind,
                        "title": notification.title,
                        "body": notification.body,
                        "payload": notification.payload,
                        "created_at": notification.created_at.isoformat(),
                    },
                },
            )
            return

        if notification.channel is NotificationChannel.EMAIL:
            if not notification.recipient_email:
                raise ValueError("email notification has no recipient address")
            await self._email.send(notification)
            return

        raise ValueError(f"unsupported channel: {notification.channel}")

    async def _mark_success(self, notification: Notification) -> None:
        now = datetime.now(UTC)
        async with session_scope(self._session_factory) as session:
            await self._repository.mark_sent(session, notification.id, now=now)
            try:
                sensitive = NotificationKind(notification.kind).is_token_link
            except ValueError:
                sensitive = False
            if sensitive:
                await self._repository.redact(session, notification.id)

            envelope = EventEnvelope.from_payload(
                NotificationEventTypes.SENT,
                NotificationSentPayload(
                    notification_id=notification.id,
                    user_id=notification.user_id,
                    kind=notification.kind,
                    channel=notification.channel.value,
                    sent_at=now,
                ),
                correlation_id=notification.correlation_id,
            )
            await self._outbox.save(session, envelope, service=self._settings.service_name)

    async def _mark_failure(self, notification: Notification, exception: Exception) -> None:
        attempts = notification.attempts
        dead = attempts >= self._settings.dispatcher_max_attempts
        delay = self._settings.dispatcher_retry_backoff_seconds * (2 ** max(0, attempts - 1))
        next_attempt_at = None if dead else datetime.now(UTC) + timedelta(seconds=delay)

        logger.warning(
            "notification.delivery_failed id=%s kind=%s channel=%s attempt=%s dead=%s error=%s",
            notification.id,
            notification.kind,
            notification.channel.value,
            attempts,
            dead,
            exception,
        )

        async with session_scope(self._session_factory) as session:
            await self._repository.mark_failed(
                session,
                notification.id,
                error=f"{type(exception).__name__}: {exception}"[:1000],
                next_attempt_at=next_attempt_at,
                dead=dead,
            )

    async def run_forever(self) -> None:
        while True:
            try:
                await self.run_once()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("notification.dispatcher_iteration_failed")
            await asyncio.sleep(self._settings.dispatcher_interval_seconds)
