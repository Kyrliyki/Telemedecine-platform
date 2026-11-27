"""Shared pytest fixtures."""

from __future__ import annotations

from collections.abc import AsyncIterator, Callable
from datetime import UTC, datetime, timedelta
from uuid import UUID, uuid4

import httpx
import jwt
import pytest

from appointment.container import Container
from appointment.domain.enums import Role
from appointment.infrastructure.config import Settings
from appointment.infrastructure.security import JwtAuthenticator
from appointment.main import create_app
from tests_appointment_service.fakes import (
    ADMIN_ID,
    DOCTOR_ID,
    PATIENT_ID,
    FakeState,
    FixedClock,
    InMemoryCache,
    InMemoryUnitOfWork,
)


@pytest.fixture
def settings() -> Settings:
    return Settings(
        env="test",
        log_level="CRITICAL",
        jwt_secret="test-secret-key-for-unit-tests-0123456789",
        cache_enabled=False,
        broker_enabled=False,
        auth_revocation_enabled=False,
        auto_migrate=False,
    )


def create_test_token(
    settings: Settings,
    subject: UUID,
    role: Role | None = None,
    *,
    roles: set[Role] | None = None,
) -> str:
    effective = roles if roles is not None else ({role} if role is not None else set())
    if not effective:
        raise ValueError("at least one role is required")
    now = datetime.now(UTC)
    payload = {
        "sub": str(subject),
        "roles": sorted(item.value.upper() for item in effective),
        "iss": settings.jwt_issuer,
        "aud": settings.jwt_audience,
        "iat": now,
        "exp": now + timedelta(minutes=5),
        "jti": str(uuid4()),
    }
    if role is not None:
        payload["role"] = role.value
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


@pytest.fixture
def state() -> FakeState:
    return FakeState()


@pytest.fixture
def clock() -> FixedClock:
    return FixedClock(datetime(2026, 11, 24, 9, 0, tzinfo=UTC))


@pytest.fixture
def cache() -> InMemoryCache:
    return InMemoryCache()


@pytest.fixture
def container(
    settings: Settings, state: FakeState, clock: FixedClock, cache: InMemoryCache
) -> Container:
    return Container(
        settings=settings,
        uow_factory=lambda: InMemoryUnitOfWork(state),
        cache=cache,
        clock=clock,
        authenticator=JwtAuthenticator(settings),
    )


@pytest.fixture
def app(settings: Settings, container: Container):
    return create_app(settings=settings, container=container)


@pytest.fixture
async def client(app) -> AsyncIterator[httpx.AsyncClient]:
    transport = httpx.ASGITransport(app=app)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as http_client:
        yield http_client


@pytest.fixture
def make_headers(settings: Settings) -> Callable[[UUID, str], dict[str, str]]:
    def _make(user_id: UUID, role: str) -> dict[str, str]:
        token = create_test_token(settings=settings, subject=user_id, role=Role(role))
        return {"Authorization": f"Bearer {token}"}

    return _make


@pytest.fixture
def patient_headers(make_headers) -> dict[str, str]:
    return make_headers(PATIENT_ID, "patient")


@pytest.fixture
def doctor_headers(make_headers) -> dict[str, str]:
    return make_headers(DOCTOR_ID, "doctor")


@pytest.fixture
def admin_headers(make_headers) -> dict[str, str]:
    return make_headers(ADMIN_ID, "admin")


@pytest.fixture
def dual_headers(settings: Settings) -> dict[str, str]:
    token = create_test_token(
        settings=settings,
        subject=DOCTOR_ID,
        roles={Role.DOCTOR, Role.PATIENT},
    )
    return {"Authorization": f"Bearer {token}"}
