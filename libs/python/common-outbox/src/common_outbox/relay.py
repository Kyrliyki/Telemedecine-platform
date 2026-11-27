from __future__ import annotations

import asyncio
import contextlib
import json
import logging
from datetime import timedelta
from typing import Protocol

from common_db import session_scope
from common_events import EventEnvelope, utcnow
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from .metrics import record_dead, record_failed, record_pending, record_sent
from .model import OutboxStatus
from .repository import OutboxRepository
from .settings import OutboxSettings

logger = logging.getLogger(__name__)


class EventPublisher(Protocol):
    async def publish(self, envelope: EventEnvelope) -> None:
        ...


class OutboxRelay:
    """Claims pending outbox rows and publishes them at-least-once."""

    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        publisher: EventPublisher,
        settings: OutboxSettings,
        service_name: str,
    ) -> None:
        self._session_factory = session_factory
        self._publisher = publisher
        self._settings = settings
        self._service_name = service_name
        self._repository = OutboxRepository()

    async def run_once(self) -> tuple[int, int, int]:
        now = utcnow()
        processing_cutoff = now - timedelta(
            seconds=self._settings.processing_timeout_seconds
        )

        async with session_scope(self._session_factory) as session:
            stuck_dead = await self._repository.mark_dead_stuck(
                session,
                self._service_name,
                processing_cutoff,
                self._settings.max_attempts,
            )
            claimed = await self._repository.claim_pending(
                session,
                self._service_name,
                self._settings.batch_size,
                self._settings.max_attempts,
                now,
                processing_cutoff,
            )

        record_pending(self._service_name, len(claimed))
        record_dead(self._service_name, stuck_dead)

        sent = failed = dead = 0
        for event in claimed:
            envelope = EventEnvelope(
                event_id=event.event_id,
                event_type=event.event_type,
                occurred_at=now,
                correlation_id=event.correlation_id,
                payload=json.loads(event.payload or "{}"),
                version=1,
            )
            try:
                await self._publisher.publish(envelope)
                async with session_scope(self._session_factory) as session:
                    await self._repository.mark_sent(session, event.event_id)
                sent += 1
            except Exception as exception:
                retry_count = event.retry_count + 1
                is_dead = retry_count >= self._settings.max_attempts
                next_attempt_at = (
                    None
                    if is_dead
                    else utcnow()
                    + timedelta(seconds=self._settings.backoff_seconds(retry_count))
                )
                async with session_scope(self._session_factory) as session:
                    await self._repository.mark_failed(
                        session,
                        event.event_id,
                        str(exception),
                        next_attempt_at,
                        is_dead,
                    )
                if is_dead:
                    dead += 1
                    logger.error(
                        "Event %s is DEAD after %s attempts: %s",
                        event.event_id,
                        retry_count,
                        exception,
                    )
                else:
                    failed += 1
                    logger.warning(
                        "Event %s failed (attempt %s/%s): %s",
                        event.event_id,
                        retry_count,
                        self._settings.max_attempts,
                        exception,
                    )

        record_sent(self._service_name, sent)
        record_failed(self._service_name, failed)
        record_dead(self._service_name, dead)

        if claimed:
            logger.debug(
                "Outbox relay service=%s sent=%s failed=%s dead=%s",
                self._service_name,
                sent,
                failed,
                dead,
            )
        return sent, failed, dead

    async def run_forever(self, stop_event: asyncio.Event | None = None) -> None:
        stop = stop_event or asyncio.Event()
        while not stop.is_set():
            try:
                await self.run_once()
            except Exception:
                logger.exception("Outbox relay iteration failed")
            with contextlib.suppress(TimeoutError):
                await asyncio.wait_for(
                    stop.wait(),
                    timeout=self._settings.relay_interval_seconds,
                )

    async def cleanup_once(self) -> int:
        cutoff = utcnow() - timedelta(seconds=self._settings.retention_seconds)
        deleted = 0
        async with session_scope(self._session_factory) as session:
            deleted += await self._repository.delete_before(
                session, self._service_name, OutboxStatus.SENT, cutoff
            )
            deleted += await self._repository.delete_before(
                session, self._service_name, OutboxStatus.DEAD, cutoff
            )
        return deleted
