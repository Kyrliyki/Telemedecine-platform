"""Internal-token guard and single-role JWT compatibility tests."""

from __future__ import annotations

import datetime as dt
from types import SimpleNamespace
from uuid import uuid4

import jwt
import pytest
from fastapi import HTTPException

from file_service.infrastructure.config import Settings
from file_service.infrastructure.security.jwt import JwtVerifier
from file_service.presentation.api.internal_auth import internal_token_guard

SECRET = "test-secret-0123456789-0123456789"


def settings(**overrides) -> Settings:
    values = {
        "jwt_algorithm": "HS256",
        "jwt_secret": SECRET,
        "jwt_issuer": "auth-service",
        "jwt_audience": "telemedicine",
        "auth_disabled": False,
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


async def test_single_role_claim_is_supported() -> None:
    actor = await JwtVerifier(settings()).verify(token(role="doctor"))

    assert actor.roles == frozenset({"doctor"})


async def test_java_roles_array_is_supported() -> None:
    actor = await JwtVerifier(settings()).verify(token(roles=["DOCTOR", "MODERATOR"]))

    assert actor.roles == frozenset({"doctor", "moderator"})
    assert actor.is_moderator is True


async def test_token_without_roles_is_not_moderator() -> None:
    actor = await JwtVerifier(settings()).verify(token())

    assert actor.roles == frozenset()
    assert actor.is_moderator is False


def request_with_token(expected: str):
    request_settings = settings(internal_api_token=expected)
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
async def test_internal_guard_rejects_missing_header() -> None:
    guard = internal_token_guard()

    with pytest.raises(HTTPException) as exc_info:
        await guard(request_with_token("secret"), None)

    assert exc_info.value.status_code == 401


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
