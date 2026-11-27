"""JWT validator tests for the ChatService adapter."""

from __future__ import annotations

from uuid import uuid4

import pytest

import chat_service.infrastructure.auth.jwt as jwt_module
from chat_service.config import Settings
from tests_chat_service.conftest import TEST_SECRET, make_token


def validator(monkeypatch, **overrides) -> jwt_module.JwtValidator:
    values = {
        "jwt_public_key": TEST_SECRET,
        "jwt_algorithm": "HS256",
        "jwt_issuer": "auth-service",
        "jwt_audience": "telemedicine",
        "auth_revocation_enabled": False,
    }
    values.update(overrides)
    settings = Settings(**values)
    monkeypatch.setattr(jwt_module, "get_settings", lambda: settings)
    return jwt_module.JwtValidator()


async def test_valid_token_returns_payload(monkeypatch) -> None:
    subject = uuid4()
    token = make_token(subject, issuer="auth-service", audience="telemedicine")

    payload = await validator(monkeypatch).validate(token)

    assert payload.subject == str(subject)


async def test_tampered_token_is_rejected(monkeypatch) -> None:
    token = make_token(uuid4(), issuer="auth-service", audience="telemedicine")

    with pytest.raises(ValueError):
        await validator(monkeypatch).validate(token + "tampered")


async def test_wrong_issuer_is_rejected(monkeypatch) -> None:
    token = make_token(uuid4(), issuer="other-service", audience="telemedicine")

    with pytest.raises(ValueError):
        await validator(monkeypatch).validate(token)


async def test_wrong_audience_is_rejected(monkeypatch) -> None:
    token = make_token(uuid4(), issuer="auth-service", audience="other-api")

    with pytest.raises(ValueError):
        await validator(monkeypatch).validate(token)


async def test_missing_subject_is_rejected(monkeypatch) -> None:
    from datetime import UTC, datetime, timedelta

    import jwt

    token = jwt.encode(
        {
            "iss": "auth-service",
            "aud": "telemedicine",
            "iat": datetime.now(UTC),
            "exp": datetime.now(UTC) + timedelta(minutes=5),
        },
        TEST_SECRET,
        algorithm="HS256",
    )

    with pytest.raises(ValueError):
        await validator(monkeypatch).validate(token)
