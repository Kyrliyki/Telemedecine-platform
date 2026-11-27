import datetime as dt
from types import SimpleNamespace

import jwt
import pytest
from common_auth import JwtValidator, internal_token_guard
from fastapi import HTTPException


def _request():
    return SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace()))


def _token(claims: dict) -> str:
    now = dt.datetime.now(dt.UTC)
    payload = {
        "iss": "auth-service",
        "aud": "telemedicine",
        "sub": "user-1",
        "iat": now,
        "exp": now + dt.timedelta(minutes=5),
        **claims,
    }
    return jwt.encode(payload, "0123456789abcdef0123456789abcdef", algorithm="HS256")


def test_validator_accepts_java_roles_array() -> None:
    validator = JwtValidator(
        issuer="auth-service",
        audience="telemedicine",
        public_key="0123456789abcdef0123456789abcdef",
        algorithms=["HS256"],
    )

    user = validator.decode(_token({"roles": ["DOCTOR", "PATIENT"]}))

    assert user.roles == frozenset({"DOCTOR", "PATIENT"})
    assert user.has_role("doctor")


def test_validator_accepts_dev_single_role_claim() -> None:
    validator = JwtValidator(
        issuer="auth-service",
        audience="telemedicine",
        public_key="0123456789abcdef0123456789abcdef",
        algorithms=["HS256"],
    )

    user = validator.decode(_token({"role": "patient"}))

    assert user.roles == frozenset({"PATIENT"})


@pytest.mark.asyncio
async def test_internal_guard_is_fail_closed_without_token() -> None:
    guard = internal_token_guard()

    with pytest.raises(HTTPException) as exc_info:
        await guard(_request(), None)

    assert exc_info.value.status_code == 503


@pytest.mark.asyncio
async def test_internal_guard_rejects_wrong_token() -> None:
    guard = internal_token_guard("expected")

    with pytest.raises(HTTPException) as exc_info:
        await guard(_request(), "wrong")

    assert exc_info.value.status_code == 401


@pytest.mark.asyncio
async def test_internal_guard_accepts_configured_token() -> None:
    guard = internal_token_guard("expected")

    assert await guard(_request(), "expected") is None
