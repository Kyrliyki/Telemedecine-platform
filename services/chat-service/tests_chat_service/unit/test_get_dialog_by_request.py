"""GetDialogByRequest use-case access-control tests."""

from __future__ import annotations

import pytest

from chat_service.application.use_cases.get_dialog_by_request import GetDialogByRequest
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from tests_chat_service.conftest import (
    CLIENT_ID,
    STRANGER_ID,
    ChatTestContext,
    build_context,
    make_dialog,
)


def use_case(context: ChatTestContext) -> GetDialogByRequest:
    return GetDialogByRequest(context.dialogs, context.messages, context.states)


async def test_participant_receives_dialog_by_request(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    found, items, next_cursor = await use_case(context).execute(
        request_id=dialog.request_id, user_id=CLIENT_ID
    )

    assert found is dialog
    assert items == []
    assert next_cursor is None


async def test_unknown_request_is_rejected(context: ChatTestContext) -> None:
    with pytest.raises(DialogNotFoundError):
        await use_case(context).execute(request_id="missing", user_id=CLIENT_ID)


async def test_stranger_cannot_read_dialog_by_request() -> None:
    context = build_context(dialog=make_dialog())
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(request_id=dialog.request_id, user_id=STRANGER_ID)
