"""Internal room-membership endpoint tests."""

from __future__ import annotations

from types import SimpleNamespace
from uuid import UUID, uuid4

import pytest
from fastapi import HTTPException

from chat_service.presentation.api import routes_internal
from tests_chat_service.conftest import CLIENT_ID, STRANGER_ID, make_dialog


class FakeSession:
    async def __aenter__(self):
        return self

    async def __aexit__(self, *args) -> None:
        return None


class FakeSessionFactory:
    def __call__(self):
        return FakeSession()


class FakeDialogRepository:
    dialog = None

    def __init__(self, session) -> None:
        self._session = session

    async def get(self, dialog_id: UUID):
        return self.dialog if self.dialog and self.dialog.id == dialog_id else None

    async def is_participant(self, dialog_id: UUID, user_id: UUID) -> bool:
        return self.dialog is not None and self.dialog.is_participant(user_id)


def request() -> SimpleNamespace:
    return SimpleNamespace(
        app=SimpleNamespace(state=SimpleNamespace(session_factory=FakeSessionFactory()))
    )


async def test_member_is_accepted(monkeypatch) -> None:
    dialog = make_dialog()
    FakeDialogRepository.dialog = dialog
    monkeypatch.setattr(routes_internal, "SqlDialogRepository", FakeDialogRepository)

    result = await routes_internal.room_membership(dialog.id, CLIENT_ID, request())

    assert result == {"member": True}


async def test_non_member_is_rejected(monkeypatch) -> None:
    dialog = make_dialog()
    FakeDialogRepository.dialog = dialog
    monkeypatch.setattr(routes_internal, "SqlDialogRepository", FakeDialogRepository)

    with pytest.raises(HTTPException) as exc_info:
        await routes_internal.room_membership(dialog.id, STRANGER_ID, request())

    assert exc_info.value.status_code == 403


async def test_missing_room_returns_404(monkeypatch) -> None:
    FakeDialogRepository.dialog = None
    monkeypatch.setattr(routes_internal, "SqlDialogRepository", FakeDialogRepository)

    with pytest.raises(HTTPException) as exc_info:
        await routes_internal.room_membership(uuid4(), CLIENT_ID, request())

    assert exc_info.value.status_code == 404


async def test_legacy_path_uses_same_check(monkeypatch) -> None:
    dialog = make_dialog()
    FakeDialogRepository.dialog = dialog
    monkeypatch.setattr(routes_internal, "SqlDialogRepository", FakeDialogRepository)

    result = await routes_internal.legacy_room_membership(dialog.id, CLIENT_ID, request())

    assert result == {"member": True}
