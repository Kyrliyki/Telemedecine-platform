"""MarkRead use-case tests."""

from __future__ import annotations

from uuid import uuid4

import pytest

from chat_service.application.use_cases.mark_read import MarkRead
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from tests_chat_service.conftest import (
    CLIENT_ID,
    STRANGER_ID,
    ChatTestContext,
    build_context,
    make_dialog,
)


def use_case(context: ChatTestContext) -> MarkRead:
    return MarkRead(context.dialogs, context.states, context.publisher)


async def test_read_receipt_is_stored_and_broadcast(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))
    up_to = uuid4()

    await use_case(context).execute(dialog_id=dialog.id, user_id=CLIENT_ID, up_to=up_to)

    assert context.states.reads == [(dialog.id, CLIENT_ID, up_to)]
    assert context.publisher.bulk_events == [
        (
            [context.dialogs.dialogs[dialog.id].participants[1].user_id],
            {
                "type": "dialog.read",
                "payload": {
                    "dialog_id": str(dialog.id),
                    "user_id": str(CLIENT_ID),
                    "up_to_message_id": str(up_to),
                },
            },
        ),
    ]


async def test_missing_dialog_is_rejected(context: ChatTestContext) -> None:
    with pytest.raises(DialogNotFoundError):
        await use_case(context).execute(
            dialog_id=uuid4(),
            user_id=CLIENT_ID,
            up_to=uuid4(),
        )


async def test_stranger_cannot_mark_as_read() -> None:
    context = build_context(dialog=make_dialog())
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(
            dialog_id=dialog.id,
            user_id=STRANGER_ID,
            up_to=uuid4(),
        )

    assert context.states.reads == []
