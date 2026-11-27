"""Internal `X-Internal-Token` guard tests."""

from __future__ import annotations

from types import SimpleNamespace

import pytest
from fastapi import HTTPException

from chat_service.config import Settings
from chat_service.presentation.api.internal_auth import internal_token_guard


def request_with_token(token: str):
    settings = Settings(internal_service_token=token)
    return SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace(settings=settings)))


@pytest.mark.asyncio
async def test_guard_is_fail_closed_without_configuration() -> None:
    guard = internal_token_guard()

    with pytest.raises(HTTPException) as exc_info:
        await guard(request_with_token(""), None)

    assert exc_info.value.status_code == 503


@pytest.mark.asyncio
async def test_guard_rejects_missing_header() -> None:
    guard = internal_token_guard()

    with pytest.raises(HTTPException) as exc_info:
        await guard(request_with_token("secret"), None)

    assert exc_info.value.status_code == 401


@pytest.mark.asyncio
async def test_guard_rejects_wrong_token() -> None:
    guard = internal_token_guard()

    with pytest.raises(HTTPException) as exc_info:
        await guard(request_with_token("secret"), "wrong")

    assert exc_info.value.status_code == 401


@pytest.mark.asyncio
async def test_guard_accepts_configured_token() -> None:
    guard = internal_token_guard()

    assert await guard(request_with_token("secret"), "secret") is None
