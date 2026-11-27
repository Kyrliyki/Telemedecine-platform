"""Entity tests: dialog membership and open/closed state."""

from __future__ import annotations

from chat_service.domain.value_objects import RequestStatus
from tests_chat_service.conftest import CLIENT_ID, STRANGER_ID, make_dialog


def test_dialog_knows_its_participants() -> None:
    dialog = make_dialog()

    assert dialog.is_participant(CLIENT_ID) is True
    assert dialog.is_participant(STRANGER_ID) is False


def test_dialog_open_state_follows_request_status() -> None:
    assert make_dialog(status=RequestStatus.OPEN).is_open() is True
    assert make_dialog(status=RequestStatus.CLOSED).is_open() is False
