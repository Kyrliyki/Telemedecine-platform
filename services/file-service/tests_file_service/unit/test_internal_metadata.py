"""Internal file-metadata endpoint tests (no database)."""

from __future__ import annotations

from types import SimpleNamespace
from uuid import uuid4

import pytest
from fastapi import HTTPException

from file_service.presentation.api.internal import file_metadata


class FakeResult:
    def __init__(self, value) -> None:
        self._value = value

    def scalar_one_or_none(self):
        return self._value


class FakeSession:
    def __init__(self, value) -> None:
        self._value = value

    async def execute(self, statement):
        return FakeResult(self._value)

    async def __aenter__(self):
        return self

    async def __aexit__(self, *args) -> None:
        return None


class FakeSessionFactory:
    def __init__(self, value) -> None:
        self._value = value

    def __call__(self):
        return FakeSession(self._value)


def request_with(attachment) -> SimpleNamespace:
    container = SimpleNamespace(session_factory=FakeSessionFactory(attachment))
    return SimpleNamespace(app=SimpleNamespace(state=SimpleNamespace(container=container)))


def attachment(**overrides):
    values = {
        "id": uuid4(),
        "uploader_id": uuid4(),
        "status": "uploaded",
        "deleted_at": None,
    }
    values.update(overrides)
    return SimpleNamespace(**values)


async def test_existing_file_is_returned() -> None:
    stored = attachment()

    result = await file_metadata(stored.id, request_with(stored), owner_id=None)

    assert result == {
        "fileId": str(stored.id),
        "ownerId": str(stored.uploader_id),
        "status": "UPLOADED",
    }


async def test_owner_filter_accepts_matching_owner() -> None:
    stored = attachment()

    result = await file_metadata(stored.id, request_with(stored), owner_id=stored.uploader_id)

    assert result["ownerId"] == str(stored.uploader_id)


async def test_owner_filter_rejects_foreign_owner() -> None:
    stored = attachment()

    with pytest.raises(HTTPException) as exc_info:
        await file_metadata(stored.id, request_with(stored), owner_id=uuid4())

    assert exc_info.value.status_code == 403


async def test_missing_file_returns_404() -> None:
    with pytest.raises(HTTPException) as exc_info:
        await file_metadata(uuid4(), request_with(None), owner_id=None)

    assert exc_info.value.status_code == 404


async def test_deleted_file_returns_404() -> None:
    stored = attachment(deleted_at="2026-01-01T00:00:00Z")

    with pytest.raises(HTTPException) as exc_info:
        await file_metadata(stored.id, request_with(stored), owner_id=None)

    assert exc_info.value.status_code == 404
