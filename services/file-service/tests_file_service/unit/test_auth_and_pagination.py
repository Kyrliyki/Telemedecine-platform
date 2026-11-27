"""Cursor and auth adapter tests."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import uuid4

import jwt
import pytest

from file_service.application.pagination import decode_cursor, encode_cursor
from file_service.domain.exceptions import AuthenticationError, ValidationError
from file_service.infrastructure.config import Settings
from file_service.infrastructure.security.jwt import JwtVerifier


def test_cursor_roundtrip() -> None:
    created_at = datetime(2026, 2, 3, 4, 5, 6, 789000, tzinfo=UTC)
    item_id = uuid4()
    cursor = encode_cursor(created_at, item_id)
    assert "=" not in cursor
    decoded_at, decoded_id = decode_cursor(cursor)
    assert decoded_at == created_at
    assert decoded_id == item_id


@pytest.mark.parametrize("cursor", ["", "%%%", "bm90LWpzb24", "eyJpZCI6ICJ4In0"])
def test_invalid_cursor(cursor: str) -> None:
    with pytest.raises(ValidationError):
        decode_cursor(cursor)


def _token(**claims) -> str:
    payload = {
        "sub": str(uuid4()),
        "roles": ["patient"],
        "iss": "https://auth.test",
        "aud": "telemed-api",
        "iat": datetime.now(UTC),
        "exp": datetime.now(UTC) + timedelta(minutes=5),
        **claims,
    }
    return jwt.encode(payload, "test-secret-that-is-long-enough-32bytes", algorithm="HS256")


async def test_jwt_verifier_extracts_actor() -> None:
    settings = Settings(
        jwt_algorithm="HS256",
        jwt_secret="test-secret-that-is-long-enough-32bytes",
        jwt_issuer="https://auth.test",
        jwt_audience="telemed-api",
        auth_revocation_enabled=False,
    )
    verifier = JwtVerifier(settings)
    subject = uuid4()
    token = _token(sub=str(subject), roles=["doctor", "moderator"])
    actor = await verifier.verify(token)
    assert actor.id == subject
    assert actor.roles == frozenset({"doctor", "moderator"})
    assert actor.is_moderator


async def test_jwt_verifier_supports_keycloak_realm_roles() -> None:
    verifier = JwtVerifier(
        Settings(
            jwt_algorithm="HS256",
            jwt_secret="test-secret-that-is-long-enough-32bytes",
            jwt_issuer="https://auth.test",
            jwt_audience="telemed-api",
            auth_revocation_enabled=False,
        )
    )
    actor = await verifier.verify(
        _token(roles=None, realm_access={"roles": ["admin"]}, scope="openid profile")
    )
    assert "admin" in actor.roles
    assert "profile" in actor.roles


async def test_jwt_verifier_rejects_bad_signature() -> None:
    verifier = JwtVerifier(
        Settings(
            jwt_algorithm="HS256",
            jwt_secret="test-secret-that-is-long-enough-32bytes",
            jwt_issuer="https://auth.test",
            jwt_audience="telemed-api",
            auth_revocation_enabled=False,
        )
    )
    token = _token()
    with pytest.raises(AuthenticationError):
        await verifier.verify(token + "tampered")


async def test_jwt_verifier_rejects_non_uuid_subject() -> None:
    verifier = JwtVerifier(
        Settings(
            jwt_algorithm="HS256",
            jwt_secret="test-secret-that-is-long-enough-32bytes",
            jwt_issuer="https://auth.test",
            jwt_audience="telemed-api",
            auth_revocation_enabled=False,
        )
    )
    with pytest.raises(AuthenticationError):
        await verifier.verify(_token(sub="user@example.com"))


def test_jwt_verifier_rejects_unknown_key_configuration() -> None:
    with pytest.raises(ValueError):
        JwtVerifier(Settings(jwt_algorithm="RS256", jwt_secret="", jwt_public_key=""))
