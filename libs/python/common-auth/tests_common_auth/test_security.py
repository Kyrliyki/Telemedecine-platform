import datetime as dt

import pytest
from common_auth import JwtAuthSettings, TokenPayload
from fastapi import HTTPException


def test_token_payload_roles() -> None:
    user = TokenPayload(
        subject="user-1",
        roles=frozenset({"DOCTOR"}),
        jti="jti-1",
        expires_at=dt.datetime.now(dt.UTC),
        issued_at=dt.datetime.now(dt.UTC),
    )

    assert user.has_role("doctor") is True
    assert user.has_role("ADMIN") is False
    with pytest.raises(HTTPException):
        user.require_role("ADMIN")


def test_auth_settings_defaults() -> None:
    settings = JwtAuthSettings()

    assert settings.issuer == "auth-service"
    assert settings.audience == "telemedicine"
