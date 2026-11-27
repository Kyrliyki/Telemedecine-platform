"""SendMessage use-case tests."""

from __future__ import annotations

from uuid import uuid4

import pytest

from chat_service.application.use_cases.send_message import SendMessage
from chat_service.domain.exceptions import (
    DialogClosedError,
    DialogNotFoundError,
    NotAParticipantError,
    RateLimitedError,
    ValidationError,
)
from chat_service.domain.value_objects import RequestStatus
from tests_chat_service.conftest import (
    CLIENT_ID,
    OPERATOR_ID,
    STRANGER_ID,
    ChatTestContext,
    build_context,
    make_dialog,
)


def use_case(context: ChatTestContext) -> SendMessage:
    return SendMessage(
        context.dialogs,
        context.messages,
        context.states,
        context.publisher,
        context.audit,
        context.rate_limiter,
    )


async def test_message_is_saved_and_published_to_other_participant(
    context: ChatTestContext,
) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    message, is_new = await use_case(context).execute(
        dialog_id=dialog.id,
        author_id=CLIENT_ID,
        content="  hello doctor  ",
    )

    assert is_new is True
    assert message.content == "hello doctor"
    assert context.messages.messages[message.id] is message
    assert dialog.id in context.dialogs.touched
    assert context.states.unread_increments == [(dialog.id, CLIENT_ID)]
    assert context.publisher.bulk_events == [
        (
            [OPERATOR_ID],
            {
                "type": "message.new",
                "payload": {
                    "id": str(message.id),
                    "dialog_id": str(dialog.id),
                    "request_id": dialog.request_id,
                    "author_id": str(CLIENT_ID),
                    "content": "hello doctor",
                    "created_at": message.created_at.isoformat(),
                    "client_msg_id": str(message.client_msg_id),
                },
            },
        ),
    ]
    assert context.audit.messages[0]["message_id"] == message.id
    assert context.audit.messages[0]["recipient_ids"] == [OPERATOR_ID]


async def test_idempotent_client_message_is_not_published_twice(
    context: ChatTestContext,
) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))
    client_msg_id = uuid4()

    first, first_new = await use_case(context).execute(
        dialog_id=dialog.id,
        author_id=CLIENT_ID,
        content="hello",
        client_msg_id=client_msg_id,
    )
    second, second_new = await use_case(context).execute(
        dialog_id=dialog.id,
        author_id=CLIENT_ID,
        content="hello",
        client_msg_id=client_msg_id,
    )

    assert first_new is True
    assert second_new is False
    assert first.id == second.id
    assert len(context.publisher.bulk_events) == 1
    assert len(context.audit.messages) == 1


async def test_rate_limited_author_is_rejected() -> None:
    context = build_context(dialog=make_dialog(), allowed=False)

    with pytest.raises(RateLimitedError):
        await use_case(context).execute(
            dialog_id=next(iter(context.dialogs.dialogs)),
            author_id=CLIENT_ID,
            content="hello",
        )


async def test_unknown_dialog_is_rejected(context: ChatTestContext) -> None:
    with pytest.raises(DialogNotFoundError):
        await use_case(context).execute(
            dialog_id=uuid4(),
            author_id=CLIENT_ID,
            content="hello",
        )


async def test_stranger_cannot_send_message(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(
            dialog_id=dialog.id,
            author_id=STRANGER_ID,
            content="hello",
        )


async def test_closed_dialog_rejects_new_messages(context: ChatTestContext) -> None:
    context.dialogs.dialogs.clear()
    dialog = make_dialog(status=RequestStatus.CLOSED)
    context.dialogs.dialogs[dialog.id] = dialog

    with pytest.raises(DialogClosedError):
        await use_case(context).execute(
            dialog_id=dialog.id,
            author_id=CLIENT_ID,
            content="hello",
        )


async def test_blank_content_is_rejected(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(ValidationError):
        await use_case(context).execute(
            dialog_id=dialog.id,
            author_id=CLIENT_ID,
            content="   ",
        )


async def test_too_long_content_is_rejected(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(ValidationError):
        await use_case(context).execute(
            dialog_id=dialog.id,
            author_id=CLIENT_ID,
            content="x" * 4097,
        )
