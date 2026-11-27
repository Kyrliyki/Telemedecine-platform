"""PostgreSQL integration tests for notification repositories (Testcontainers)."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from notification_service.domain.entities import NotificationDraft
from notification_service.domain.enums import NotificationChannel, NotificationStatus
from notification_service.infrastructure.db.repositories import (
    SqlAlchemyNotificationRepository,
)

pytestmark = pytest.mark.integration


def _draft(
    *,
    user_id: str = "user-1",
    dedup_key: str = "dedup-1",
    channel: NotificationChannel = NotificationChannel.IN_APP,
) -> NotificationDraft:
    return NotificationDraft(
        user_id=user_id,
        kind="auth.invited",
        channel=channel,
        title="Invitation",
        body="You are invited",
        payload={"token": "secret"},
        recipient_email="user@example.com" if channel is NotificationChannel.EMAIL else None,
        dedup_key=dedup_key,
    )


async def test_add_many_is_idempotent_by_dedup_key(session_factory) -> None:
    repository = SqlAlchemyNotificationRepository()

    async with session_factory() as session:
        assert await repository.add_many(session, [_draft()]) == 1
        await session.commit()

    async with session_factory() as session:
        assert await repository.add_many(session, [_draft()]) == 0
        await session.commit()


async def test_claim_mark_sent_and_list(session_factory) -> None:
    repository = SqlAlchemyNotificationRepository()
    now = datetime.now(UTC)

    async with session_factory() as session:
        await repository.add_many(
            session,
            [
                _draft(user_id="user-1", dedup_key="in-app"),
                _draft(user_id="user-1", dedup_key="email", channel=NotificationChannel.EMAIL),
            ],
        )
        await session.commit()

    async with session_factory() as session:
        claimed = await repository.claim_pending(
            session,
            limit=10,
            max_attempts=3,
            now=now,
            processing_cutoff=now - timedelta(minutes=1),
        )
        await session.commit()

    assert len(claimed) == 2
    assert {item.status for item in claimed} == {NotificationStatus.PROCESSING}
    assert {item.attempts for item in claimed} == {1}

    in_app = next(item for item in claimed if item.channel is NotificationChannel.IN_APP)

    async with session_factory() as session:
        await repository.mark_sent(session, in_app.id, now=now)
        await session.commit()

    async with session_factory() as session:
        items, total = await repository.list_for_user(
            session, user_id="user-1", unread_only=False, limit=10, offset=0
        )
        assert total == 1
        assert {item.channel for item in items} == {NotificationChannel.IN_APP}

        found = await repository.get_for_user(session, notification_id=in_app.id, user_id="user-1")
        assert found is not None
        assert found.status is NotificationStatus.SENT


async def test_recover_stuck_and_fail_dead(session_factory) -> None:
    repository = SqlAlchemyNotificationRepository()
    old_now = datetime.now(UTC) - timedelta(minutes=10)

    async with session_factory() as session:
        await repository.add_many(session, [_draft()])
        await session.commit()

    async with session_factory() as session:
        claimed = await repository.claim_pending(
            session,
            limit=10,
            max_attempts=3,
            now=old_now,
            processing_cutoff=old_now - timedelta(minutes=1),
        )
        await session.commit()
    assert len(claimed) == 1

    now = datetime.now(UTC)
    async with session_factory() as session:
        recovered = await repository.recover_stuck(
            session,
            now=now,
            cutoff=now - timedelta(minutes=1),
            max_attempts=3,
        )
        await session.commit()
    assert recovered == 1

    async with session_factory() as session:
        item = await repository.get_for_user(
            session, notification_id=claimed[0].id, user_id="user-1"
        )
        assert item is not None
        assert item.status is NotificationStatus.FAILED

        await repository.mark_failed(
            session,
            claimed[0].id,
            error="permanent failure",
            next_attempt_at=None,
            dead=True,
        )
        await session.commit()

    async with session_factory() as session:
        item = await repository.get_for_user(
            session, notification_id=claimed[0].id, user_id="user-1"
        )
        assert item is not None
        assert item.status is NotificationStatus.DEAD


async def test_read_flags_and_contacts(session_factory) -> None:
    repository = SqlAlchemyNotificationRepository()
    now = datetime.now(UTC)

    async with session_factory() as session:
        await repository.add_many(session, [_draft(user_id="user-2", dedup_key="read")])
        await session.commit()

    async with session_factory() as session:
        items, _ = await repository.list_for_user(
            session, user_id="user-2", unread_only=True, limit=10, offset=0
        )
        assert len(items) == 1
        assert await repository.unread_count(session, user_id="user-2") == 1

        assert await repository.mark_read(
            session, notification_id=items[0].id, user_id="user-2", now=now
        )
        await session.commit()

    async with session_factory() as session:
        assert await repository.unread_count(session, user_id="user-2") == 0

        await repository.upsert_contact(session, user_id="user-2", email="new@example.com", now=now)
        await repository.upsert_contact(
            session, user_id="user-2", email="newer@example.com", now=now
        )
        await session.commit()

    async with session_factory() as session:
        contacts = await repository.get_contacts(session, user_ids={"user-2", "missing"})
        assert contacts == {"user-2": "newer@example.com"}

