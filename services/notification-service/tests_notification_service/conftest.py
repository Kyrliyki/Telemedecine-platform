"""Test helpers: in-memory repository, fake session factory and app."""

from __future__ import annotations

from collections.abc import AsyncIterator
from datetime import UTC, datetime, timedelta
from typing import Any
from uuid import UUID, uuid4

import jwt
import pytest
from httpx import ASGITransport, AsyncClient

from notification_service.application.ports import Clock
from notification_service.config import Settings
from notification_service.container import Container
from notification_service.domain.entities import Notification, NotificationDraft
from notification_service.domain.enums import NotificationChannel, NotificationStatus
from notification_service.main import create_app


class FakeSession:
    async def __aenter__(self) -> FakeSession:
        return self

    async def __aexit__(self, *args: Any) -> None:
        return None

    def begin(self) -> FakeSession:
        return self


class FakeSessionFactory:
    def __call__(self) -> FakeSession:
        return FakeSession()


class InMemoryNotificationRepository:
    def __init__(self) -> None:
        self.items: dict[UUID, Notification] = {}
        self.contacts: dict[str, str] = {}
        self.sent: set[UUID] = set()
        self.failed: dict[UUID, tuple[str, datetime | None, bool]] = {}
        self.redacted: set[UUID] = set()
        self.recovered = 0

    async def add_many(self, session: Any, drafts: list[NotificationDraft]) -> int:
        created = 0
        existing = {item.dedup_key for item in self.items.values()}
        for draft in drafts:
            if draft.dedup_key in existing:
                continue
            notification = Notification(
                id=uuid4(),
                user_id=draft.user_id,
                kind=draft.kind,
                channel=draft.channel,
                title=draft.title,
                body=draft.body,
                payload=draft.payload,
                status=NotificationStatus.PENDING,
                attempts=0,
                dedup_key=draft.dedup_key,
                recipient_email=draft.recipient_email,
                correlation_id=draft.correlation_id,
                error=None,
                next_attempt_at=None,
                processing_started_at=None,
                created_at=datetime.now(UTC),
                sent_at=None,
                read_at=None,
            )
            self.items[notification.id] = notification
            existing.add(draft.dedup_key)
            created += 1
        return created

    async def claim_pending(
        self,
        session: Any,
        *,
        limit: int,
        max_attempts: int,
        now: datetime,
        processing_cutoff: datetime,
    ) -> list[Notification]:
        pending = [
            item
            for item in self.items.values()
            if item.status in {NotificationStatus.PENDING, NotificationStatus.FAILED}
        ][:limit]
        claimed = []
        for item in pending:
            item.attempts += 1
            item.status = NotificationStatus.PROCESSING
            claimed.append(item)
        return claimed

    async def mark_sent(self, session: Any, notification_id: UUID, *, now: datetime) -> None:
        item = self.items[notification_id]
        item.status = NotificationStatus.SENT
        item.sent_at = now
        self.sent.add(notification_id)

    async def redact(self, session: Any, notification_id: UUID) -> None:
        self.redacted.add(notification_id)
        self.items[notification_id].body = "Ссылка отправлена на email."
        self.items[notification_id].payload = {}

    async def recover_stuck(
        self,
        session: Any,
        *,
        now: datetime,
        cutoff: datetime,
        max_attempts: int,
    ) -> int:
        return self.recovered

    async def mark_failed(
        self,
        session: Any,
        notification_id: UUID,
        *,
        error: str,
        next_attempt_at: datetime | None,
        dead: bool,
    ) -> None:
        self.failed[notification_id] = (error, next_attempt_at, dead)
        self.items[notification_id].status = (
            NotificationStatus.DEAD if dead else NotificationStatus.FAILED
        )

    async def list_for_user(
        self,
        session: Any,
        *,
        user_id: str,
        unread_only: bool,
        limit: int,
        offset: int,
    ) -> tuple[list[Notification], int]:
        items = [
            item
            for item in self.items.values()
            if item.user_id == user_id
            and item.channel is NotificationChannel.IN_APP
            and (not unread_only or item.read_at is None)
        ]
        items.sort(key=lambda item: item.created_at, reverse=True)
        return items[offset : offset + limit], len(items)

    async def get_for_user(
        self,
        session: Any,
        *,
        notification_id: UUID,
        user_id: str,
    ) -> Notification | None:
        item = self.items.get(notification_id)
        if item is None or item.user_id != user_id:
            return None
        return item

    async def unread_count(self, session: Any, *, user_id: str) -> int:
        return sum(
            1
            for item in self.items.values()
            if item.user_id == user_id
            and item.channel is NotificationChannel.IN_APP
            and item.read_at is None
        )

    async def mark_read(
        self,
        session: Any,
        *,
        notification_id: UUID,
        user_id: str,
        now: datetime,
    ) -> bool:
        item = self.items.get(notification_id)
        if item is None or item.user_id != user_id:
            return False
        item.read_at = now
        return True

    async def mark_all_read(self, session: Any, *, user_id: str, now: datetime) -> int:
        updated = 0
        for item in self.items.values():
            if (
                item.user_id == user_id
                and item.channel is NotificationChannel.IN_APP
                and item.read_at is None
            ):
                item.read_at = now
                updated += 1
        return updated

    async def upsert_contact(
        self,
        session: Any,
        *,
        user_id: str,
        email: str,
        now: datetime,
    ) -> None:
        self.contacts[user_id] = email

    async def get_contacts(self, session: Any, *, user_ids: set[str]) -> dict[str, str]:
        return {user_id: self.contacts[user_id] for user_id in user_ids if user_id in self.contacts}


class FakeInApp:
    def __init__(self) -> None:
        self.messages: list[tuple[str, dict]] = []

    async def publish(self, *, user_id: str, message: dict) -> None:
        self.messages.append((user_id, message))

    async def ping(self) -> bool:
        return True

    async def close(self) -> None:
        return None


class FakeEmail:
    def __init__(self, fail: bool = False) -> None:
        self.fail = fail
        self.sent: list[Notification] = []

    async def send(self, notification: Notification) -> None:
        if self.fail:
            raise RuntimeError("smtp down")
        self.sent.append(notification)


class FakeClock(Clock):
    def now(self) -> datetime:
        return datetime.now(UTC)


class FakeDispatcher:
    def __init__(self) -> None:
        self.calls = 0

    async def run_once(self) -> int:
        self.calls += 1
        return 0


def make_settings(**overrides: Any) -> Settings:
    values: dict[str, Any] = {
        "environment": "test",
        "jwt_secret": "test-secret-0123456789-0123456789",
        "jwt_issuer": "auth-service",
        "jwt_audience": "telemedicine",
        "email_enabled": False,
        "in_app_enabled": True,
        "dev_endpoints_enabled": True,
        "frontend_url": "http://frontend.test",
        "smtp_username": "test@gmail.com",
        "smtp_password": "app-password",
        "smtp_from": "test@gmail.com",
        "database_url": "postgresql+asyncpg://unused:unused@localhost/unused",
        "auth_revocation_enabled": False,
    }
    values.update(overrides)
    return Settings(**values)


def make_token(
    settings: Settings,
    subject: str = "patient-1",
    roles: list[str] | None = None,
) -> str:
    now = datetime.now(UTC)
    return jwt.encode(
        {
            "sub": subject,
            "roles": roles or ["patient"],
            "iss": settings.jwt_issuer,
            "aud": settings.jwt_audience,
            "iat": now,
            "exp": now + timedelta(hours=1),
        },
        settings.jwt_secret,
        algorithm="HS256",
    )


def build_test_app(
    *,
    repository: InMemoryNotificationRepository | None = None,
    settings: Settings | None = None,
    dispatcher: Any = None,
):
    settings = settings or make_settings()
    repository = repository or InMemoryNotificationRepository()
    container = Container(
        settings=settings,
        engine=None,  # type: ignore[arg-type]
        session_factory=FakeSessionFactory(),
        repository=repository,  # type: ignore[arg-type]
        ingest=None,  # type: ignore[arg-type]
        dispatcher=dispatcher or FakeDispatcher(),  # type: ignore[arg-type]
        publisher=None,  # type: ignore[arg-type]
        relay=None,  # type: ignore[arg-type]
        in_app=FakeInApp(),
        email=FakeEmail(),
        router=None,  # type: ignore[arg-type]
        consumers=[],
        managed=False,
    )
    app = create_app(settings=settings, container=container)
    return app, repository


@pytest.fixture
def settings() -> Settings:
    return make_settings()


@pytest.fixture
def repository() -> InMemoryNotificationRepository:
    return InMemoryNotificationRepository()


@pytest.fixture
async def client(settings, repository) -> AsyncIterator[AsyncClient]:
    app, _ = build_test_app(settings=settings, repository=repository)
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://testserver") as http_client:
        yield http_client
