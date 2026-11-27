"""SendTyping use-case tests."""

from __future__ import annotations

from uuid import uuid4

import pytest

from chat_service.application.use_cases.send_typing import SendTyping
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from tests_chat_service.conftest import CLIENT_ID, STRANGER_ID, ChatTestContext


def use_case(context: ChatTestContext) -> SendTyping:
    return SendTyping(context.dialogs, context.publisher)


async def test_typing_is_sent_only_to_other_participants(
    context: ChatTestContext,
) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    await use_case(context).execute(dialog_id=dialog.id, user_id=CLIENT_ID)

    assert len(context.publisher.bulk_events) == 1
    recipients, event = context.publisher.bulk_events[0]
    assert recipients == [dialog.participants[1].user_id]
    assert event["type"] == "typing"
    assert event["payload"]["user_id"] == str(CLIENT_ID)


async def test_stranger_cannot_send_typing(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(dialog_id=dialog.id, user_id=STRANGER_ID)


async def test_missing_dialog_is_rejected(context: ChatTestContext) -> None:
    with pytest.raises(DialogNotFoundError):
        await use_case(context).execute(dialog_id=uuid4(), user_id=CLIENT_ID)
