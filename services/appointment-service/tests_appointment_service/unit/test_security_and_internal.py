"""JWT compatibility and internal-token guard tests."""

from __future__ import annotations

import datetime as dt
from types import SimpleNamespace
from uuid import uuid4

import jwt
import pytest
from fastapi import HTTPException

from appointment.domain.enums import Role
from appointment.domain.errors import AuthenticationError
from appointment.infrastructure.config import Settings
from appointment.infrastructure.security import JwtAuthenticator
from appointment.presentation.internal_auth import internal_token_guard

SECRET = "test-secret-0123456789-0123456789"


def settings(**overrides) -> Settings:
    values = {
        "env": "test",
        "jwt_secret": SECRET,
        "jwt_algorithm": "HS256",
        "jwt_issuer": "auth-service",
        "jwt_audience": "telemedicine",
        "cache_enabled": False,
        "broker_enabled": False,
        "auto_migrate": False,
        "auth_revocation_enabled": False,
    }
    values.update(overrides)
    return Settings(**values)


def token(**claims) -> str:
    now = dt.datetime.now(dt.UTC)
    payload = {
        "sub": str(uuid4()),
        "iss": "auth-service",
        "aud": "telemedicine",
        "iat": now,
        "exp": now + dt.timedelta(minutes=5),
        **claims,
    }
    return jwt.encode(payload, SECRET, algorithm="HS256")


async def test_java_roles_array_is_accepted() -> None:
    claims = await JwtAuthenticator(settings()).authenticate(token(roles=["PATIENT", "DOCTOR"]))

    assert claims.roles == frozenset({Role.PATIENT, Role.DOCTOR})


async def test_all_roles_are_preserved() -> None:
    claims = await JwtAuthenticator(settings()).authenticate(token(roles=["DOCTOR", "ADMIN"]))

    assert claims.roles == frozenset({Role.DOCTOR, Role.ADMIN})


async def test_dev_single_role_claim_is_accepted() -> None:
    claims = await JwtAuthenticator(settings()).authenticate(token(role="patient"))

    assert claims.roles == frozenset({Role.PATIENT})


async def test_token_without_supported_role_is_rejected() -> None:
    with pytest.raises(AuthenticationError):
        await JwtAuthenticator(settings()).authenticate(token(roles=["SUPPORT"]))


async def test_token_with_wrong_signature_is_rejected() -> None:
    bad = jwt.encode(
        {
            "sub": str(uuid4()),
            "role": "patient",
            "iss": "auth-service",
            "aud": "telemedicine",
            "iat": dt.datetime.now(dt.UTC),
            "exp": dt.datetime.now(dt.UTC) + dt.timedelta(minutes=5),
        },
        "another-secret-0123456789-0123456789",
        algorithm="HS256",
    )

    with pytest.raises(AuthenticationError):
        await JwtAuthenticator(settings()).authenticate(bad)


def request_with_token(expected: str):
    request_settings = settings(internal_service_token=expected)
    return SimpleNamespace(
        app=SimpleNamespace(state=SimpleNamespace(settings=request_settings))
    )


@pytest.mark.asyncio
async def test_internal_guard_is_fail_closed() -> None:
    guard = internal_token_guard()

    with pytest.raises(HTTPException) as exc_info:
        await guard(request_with_token(""), None)

    assert exc_info.value.status_code == 503


@pytest.mark.asyncio
async def test_internal_guard_rejects_wrong_token() -> None:
    guard = internal_token_guard()

    with pytest.raises(HTTPException) as exc_info:
        await guard(request_with_token("secret"), "wrong")

    assert exc_info.value.status_code == 401


@pytest.mark.asyncio
async def test_internal_guard_accepts_configured_token() -> None:
    guard = internal_token_guard()

    assert await guard(request_with_token("secret"), "secret") is None
