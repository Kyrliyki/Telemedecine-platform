"""Общие фикстуры и фейковые адаптеры для тестов."""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from typing import Any

import jwt
import pytest
from httpx import ASGITransport, AsyncClient

from video_service.application.dto import Actor
from video_service.application.ports import (
    LiveKitRoomEvent,
    RoomEventKind,
    UnitOfWorkFactory,
    WebhookParser,
    WebhookVerificationError,
)
from video_service.application.use_cases import (
    CreateCallUseCase,
    EndCallUseCase,
    GetCallUseCase,
    HandleLiveKitEventUseCase,
    JoinCallUseCase,
    ListCallsUseCase,
)
from video_service.composition import Container
from video_service.config import Settings
from video_service.domain.events import DomainEvent
from video_service.domain.value_objects import CallId, RoomName
from video_service.infrastructure.auth.jwt import JwtVerifier
from video_service.infrastructure.persistence.memory import (
    InMemoryCallStore,
    InMemoryUnitOfWork,
)
from video_service.presentation.app import create_app

TEST_SECRET = "test-secret-0123456789-0123456789"
TEST_NOW = datetime(2026, 1, 15, 10, 0, tzinfo=UTC)


class FakeClock:
    def __init__(self, now: datetime = TEST_NOW) -> None:
        self._now = now

    def now(self) -> datetime:
        return self._now

    def advance(self, delta: timedelta) -> None:
        self._now += delta


class FakeIdGenerator:
    def __init__(self) -> None:
        self._counter = 0

    def new_call_id(self) -> CallId:
        self._counter += 1
        return CallId(uuid.UUID(int=self._counter))


class FakeLiveKitGateway:
    def __init__(self) -> None:
        self.created_rooms: list[dict[str, Any]] = []
        self.deleted_rooms: list[RoomName] = []
        self.healthy = True
        self.fail_create = False
        self.fail_delete = False
        self.closed = False

    async def create_room(
        self,
        *,
        room_name: RoomName,
        metadata: dict[str, str],
        max_participants: int,
    ) -> None:
        if self.fail_create:
            from video_service.application.ports import LiveKitGatewayError

            raise LiveKitGatewayError("boom")
        self.created_rooms.append(
            {
                "room_name": room_name,
                "metadata": metadata,
                "max_participants": max_participants,
            },
        )

    async def delete_room(self, *, room_name: RoomName) -> None:
        if self.fail_delete:
            from video_service.application.ports import LiveKitGatewayError

            raise LiveKitGatewayError("boom")
        self.deleted_rooms.append(room_name)

    async def create_participant_token(
        self,
        *,
        room_name: RoomName,
        identity: str,
        display_name: str,
        ttl: timedelta,
        metadata: dict[str, str],
    ) -> str:
        return f"token.{room_name.value}.{identity}"

    async def healthcheck(self) -> bool:
        return self.healthy

    async def aclose(self) -> None:
        self.closed = True


class RecordingPublisher:
    def __init__(self) -> None:
        self.events: list[DomainEvent] = []

    async def publish(self, event: DomainEvent) -> None:
        self.events.append(event)


class RecordingNotifier:
    def __init__(self) -> None:
        self.calls: list[Any] = []

    async def notify_started(self, call: Any) -> None:
        self.calls.append(call)


class FakeWebhookParser(WebhookParser):
    def __init__(self) -> None:
        self.next_event: LiveKitRoomEvent | None = None
        self.reject = False

    def parse(self, body: bytes, auth_header: str) -> LiveKitRoomEvent | None:
        if self.reject:
            raise WebhookVerificationError("bad signature")
        return self.next_event


class FailingCallRepository:
    async def get(self, call_id: CallId) -> None:
        return None

    async def get_current_by_appointment(self, appointment_id: uuid.UUID) -> None:
        return None

    async def get_by_room_name(self, room_name: RoomName) -> None:
        return None

    async def list(self, **kwargs: Any) -> list[Any]:
        return []

    async def add(self, call: Any) -> None:
        raise RuntimeError("db is down")

    async def update(self, call: Any) -> None:
        raise RuntimeError("db is down")


class FailingUnitOfWork(InMemoryUnitOfWork):
    def __init__(self) -> None:
        super().__init__(InMemoryCallStore())
        self.calls = FailingCallRepository()  # type: ignore[assignment]


@dataclass
class VideoTestContext:
    container: Container
    store: InMemoryCallStore
    livekit: FakeLiveKitGateway
    publisher: RecordingPublisher
    notifier: RecordingNotifier
    clock: FakeClock
    ids: FakeIdGenerator
    webhook_parser: FakeWebhookParser
    events: list[LiveKitRoomEvent] = field(default_factory=list)


def build_test_context(
    settings: Settings | None = None,
    *,
    uow_factory: UnitOfWorkFactory | None = None,
) -> VideoTestContext:
    settings = settings or test_settings()
    store = InMemoryCallStore()
    livekit = FakeLiveKitGateway()
    publisher = RecordingPublisher()
    notifier = RecordingNotifier()
    clock = FakeClock()
    ids = FakeIdGenerator()
    webhook_parser = FakeWebhookParser()
    factory: UnitOfWorkFactory = uow_factory or (lambda: InMemoryUnitOfWork(store))

    container = Container(
        settings=settings,
        create_call=CreateCallUseCase(
            uow_factory=factory,
            livekit=livekit,
            clock=clock,
            ids=ids,
            publisher=publisher,
            notifier=notifier,
        ),
        join_call=JoinCallUseCase(
            uow_factory=factory,
            livekit=livekit,
            clock=clock,
            token_ttl=timedelta(seconds=settings.livekit_token_ttl_seconds),
            public_url=settings.livekit_public_url,
        ),
        end_call=EndCallUseCase(
            uow_factory=factory,
            livekit=livekit,
            clock=clock,
            publisher=publisher,
        ),
        get_call=GetCallUseCase(uow_factory=factory),
        list_calls=ListCallsUseCase(uow_factory=factory),
        handle_livekit_event=HandleLiveKitEventUseCase(
            uow_factory=factory,
            clock=clock,
            publisher=publisher,
        ),
        livekit=livekit,  # type: ignore[arg-type]
        publisher=publisher,
        notifier=notifier,
        webhook_parser=webhook_parser,
        token_verifier=JwtVerifier(
            secret=TEST_SECRET,
            algorithms=["HS256"],
            audience=None,
            issuer=None,
        ),
    )
    return VideoTestContext(
        container=container,
        store=store,
        livekit=livekit,
        publisher=publisher,
        notifier=notifier,
        clock=clock,
        ids=ids,
        webhook_parser=webhook_parser,
    )


def test_settings(**overrides: Any) -> Settings:
    defaults: dict[str, Any] = {
        "environment": "test",
        "auth_enabled": True,
        "jwt_secret": TEST_SECRET,
        "jwt_algorithm": "HS256",
        "jwt_audience": "",
        "jwt_issuer": "",
        "storage_backend": "memory",
        "livekit_public_url": "ws://livekit.test:7880",
        "livekit_token_ttl_seconds": 900,
    }
    defaults.update(overrides)
    return Settings(**defaults)


def make_token(
    subject: str,
    roles: list[str],
    *,
    name: str | None = None,
    expires_in: timedelta = timedelta(hours=1),
) -> str:
    now = datetime.now(UTC)
    payload: dict[str, Any] = {
        "sub": subject,
        "roles": roles,
        "iss": "auth-service",
        "aud": "telemedicine",
        "iat": now,
        "exp": now + expires_in,
    }
    if name:
        payload["name"] = name
    return jwt.encode(payload, TEST_SECRET, algorithm="HS256")


def doctor_actor(identity: str = "doctor-1") -> Actor:
    return Actor(identity=identity, roles=frozenset({"doctor"}))


def patient_actor(identity: str = "patient-1") -> Actor:
    return Actor(identity=identity, roles=frozenset({"patient"}))


def admin_actor(identity: str = "admin-1") -> Actor:
    return Actor(identity=identity, roles=frozenset({"admin"}))


@pytest.fixture
def context() -> VideoTestContext:
    return build_test_context()


@pytest.fixture
def app(context: VideoTestContext):
    return create_app(context.container, context.container.settings)


@pytest.fixture
async def client(app):
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as http_client:
        yield http_client


@pytest.fixture
def auth_headers() -> dict[str, str]:
    return {"Authorization": f"Bearer {make_token('doctor-1', ['doctor'])}"}


__all__ = [
    "VideoTestContext",
    "TEST_SECRET",
    "admin_actor",
    "build_test_context",
    "doctor_actor",
    "make_token",
    "patient_actor",
    "test_settings",
    "RoomEventKind",
]
