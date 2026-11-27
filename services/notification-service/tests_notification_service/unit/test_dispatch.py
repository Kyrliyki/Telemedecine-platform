"""Dispatcher tests with in-memory adapters."""

from __future__ import annotations

from uuid import uuid4

import pytest

from notification_service.application.use_cases import dispatch as dispatch_module
from notification_service.application.use_cases.dispatch import NotificationDispatcher
from notification_service.domain.entities import NotificationDraft
from notification_service.domain.enums import NotificationChannel, NotificationStatus
from tests_notification_service.conftest import (
    FakeEmail,
    FakeInApp,
    FakeSessionFactory,
    InMemoryNotificationRepository,
    make_settings,
)


class FakeOutbox:
    def __init__(self) -> None:
        self.events = []

    async def save(self, session, envelope, service):
        self.events.append(envelope)


def draft(*, channel: NotificationChannel = NotificationChannel.IN_APP) -> NotificationDraft:
    return NotificationDraft(
        user_id="patient-1",
        kind="system.test",
        channel=channel,
        title="Тест",
        body="Проверка",
        payload={},
        recipient_email="patient@example.test" if channel is NotificationChannel.EMAIL else None,
        dedup_key=f"test:{uuid4()}",
    )


@pytest.fixture
def outbox(monkeypatch) -> FakeOutbox:
    fake = FakeOutbox()
    monkeypatch.setattr(dispatch_module, "OutboxRepository", lambda: fake)
    return fake


async def test_in_app_delivery_marks_sent_and_writes_outbox(outbox) -> None:
    repository = InMemoryNotificationRepository()
    await repository.add_many(None, [draft()])
    in_app = FakeInApp()
    dispatcher = NotificationDispatcher(
        session_factory=FakeSessionFactory(),
        repository=repository,
        in_app=in_app,
        email=FakeEmail(),
        settings=make_settings(),
    )

    processed = await dispatcher.run_once()

    assert processed == 1
    assert len(in_app.messages) == 1
    assert in_app.messages[0][1]["type"] == "notification.created"
    item = next(iter(repository.items.values()))
    assert item.status is NotificationStatus.SENT
    assert len(outbox.events) == 1
    assert outbox.events[0].event_type == "notification.notification.sent.v1"


async def test_email_failure_marks_dead_after_max_attempts(outbox) -> None:
    repository = InMemoryNotificationRepository()
    await repository.add_many(None, [draft(channel=NotificationChannel.EMAIL)])
    dispatcher = NotificationDispatcher(
        session_factory=FakeSessionFactory(),
        repository=repository,
        in_app=FakeInApp(),
        email=FakeEmail(fail=True),
        settings=make_settings(dispatcher_max_attempts=1),
    )

    processed = await dispatcher.run_once()

    assert processed == 1
    item = next(iter(repository.items.values()))
    assert item.status is NotificationStatus.DEAD
    assert item.id in repository.failed
    assert not outbox.events


async def test_token_email_is_redacted_after_send(outbox) -> None:
    repository = InMemoryNotificationRepository()
    token_draft = NotificationDraft(
        user_id="patient-1",
        kind="auth.email_verification",
        channel=NotificationChannel.EMAIL,
        title="Подтвердите email",
        body="Ссылка: http://frontend.test/verify?token=secret",
        payload={},
        recipient_email="patient@example.test",
        dedup_key=f"test:{uuid4()}",
    )
    await repository.add_many(None, [token_draft])
    dispatcher = NotificationDispatcher(
        session_factory=FakeSessionFactory(),
        repository=repository,
        in_app=FakeInApp(),
        email=FakeEmail(),
        settings=make_settings(email_enabled=True),
    )

    await dispatcher.run_once()

    item = next(iter(repository.items.values()))
    assert item.id in repository.redacted
    assert "secret" not in item.body
    assert item.status is NotificationStatus.SENT
