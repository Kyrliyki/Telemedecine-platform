"""GetHistory use-case tests."""

from __future__ import annotations

from uuid import uuid4

import pytest

from chat_service.application.use_cases.get_history import GetHistory
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from tests_chat_service.conftest import (
    CLIENT_ID,
    STRANGER_ID,
    ChatTestContext,
    build_context,
    make_dialog,
)


def use_case(context: ChatTestContext) -> GetHistory:
    return GetHistory(context.dialogs, context.messages)


async def test_participant_receives_message_page(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))
    await context.messages.save(
        message_id=uuid4(),
        dialog_id=dialog.id,
        author_id=CLIENT_ID,
        content="first",
        client_msg_id=uuid4(),
    )

    items, next_cursor = await use_case(context).execute(
        dialog_id=dialog.id,
        user_id=CLIENT_ID,
        cursor=None,
        limit=10,
    )

    assert [message.content for message in items] == ["first"]
    assert next_cursor is None


async def test_missing_dialog_is_rejected(context: ChatTestContext) -> None:
    with pytest.raises(DialogNotFoundError):
        await use_case(context).execute(
            dialog_id=uuid4(),
            user_id=CLIENT_ID,
            cursor=None,
            limit=10,
        )


async def test_stranger_cannot_read_history() -> None:
    context = build_context(dialog=make_dialog())
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(
            dialog_id=dialog.id,
            user_id=STRANGER_ID,
            cursor=None,
            limit=10,
        )
