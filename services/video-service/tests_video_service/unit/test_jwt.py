"""JwtVerifier unit tests."""

from __future__ import annotations

import datetime as dt

import jwt
import pytest

from video_service.application.ports import InvalidTokenError
from video_service.domain.enums import ActorRole
from video_service.infrastructure.auth.jwt import JwtVerifier

SECRET = "test-secret-0123456789-0123456789"


def verifier() -> JwtVerifier:
    return JwtVerifier(secret=SECRET, algorithms=["HS256"])


def token(**claims) -> str:
    now = dt.datetime.now(dt.UTC)
    payload = {
        "sub": "doctor-1",
        "iss": "auth-service",
        "aud": "telemedicine",
        "iat": now,
        "exp": now + dt.timedelta(minutes=5),
        **claims,
    }
    return jwt.encode(payload, SECRET, algorithm="HS256")


async def test_roles_array_is_extracted() -> None:
    actor = await verifier().verify(token(roles=["DOCTOR", "PATIENT"]))

    assert actor.identity == "doctor-1"
    assert actor.roles == frozenset({ActorRole.DOCTOR, ActorRole.PATIENT})


async def test_single_role_claim_is_supported() -> None:
    actor = await verifier().verify(token(role="doctor"))

    assert actor.roles == frozenset({ActorRole.DOCTOR})


async def test_display_name_falls_back_to_email() -> None:
    actor = await verifier().verify(token(roles=["patient"], email="patient@example.test"))

    assert actor.display_name == "patient@example.test"


async def test_unknown_roles_are_ignored() -> None:
    actor = await verifier().verify(token(roles=["SUPPORT"]))

    assert actor.roles == frozenset()


async def test_missing_subject_is_rejected() -> None:
    now = dt.datetime.now(dt.UTC)
    raw = jwt.encode(
        {
            "iss": "auth-service",
            "aud": "telemedicine",
            "iat": now,
            "exp": now + dt.timedelta(minutes=5),
        },
        SECRET,
        algorithm="HS256",
    )

    with pytest.raises(InvalidTokenError):
        await verifier().verify(raw)


async def test_expired_token_is_rejected() -> None:
    now = dt.datetime.now(dt.UTC)
    raw = jwt.encode(
        {
            "sub": "doctor-1",
            "iss": "auth-service",
            "aud": "telemedicine",
            "iat": now - dt.timedelta(minutes=10),
            "exp": now - dt.timedelta(minutes=5),
        },
        SECRET,
        algorithm="HS256",
    )

    with pytest.raises(InvalidTokenError):
        await verifier().verify(raw)


async def test_bad_signature_is_rejected() -> None:
    raw = jwt.encode(
        {
            "sub": "doctor-1",
            "iss": "auth-service",
            "aud": "telemedicine",
            "iat": dt.datetime.now(dt.UTC),
            "exp": dt.datetime.now(dt.UTC) + dt.timedelta(minutes=5),
        },
        "another-secret-0123456789-0123456789",
        algorithm="HS256",
    )

    with pytest.raises(InvalidTokenError):
        await verifier().verify(raw)
