"""UpdateDialog use-case tests."""

from __future__ import annotations

from uuid import uuid4

import pytest

from chat_service.application.use_cases.update_dialog import UpdateDialog
from chat_service.domain.entities import Participant
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from chat_service.domain.value_objects import ParticipantRole, RequestStatus
from tests_chat_service.conftest import (
    CLIENT_ID,
    STRANGER_ID,
    ChatTestContext,
    build_context,
    make_dialog,
)


def use_case(context: ChatTestContext) -> UpdateDialog:
    return UpdateDialog(context.dialogs, context.states, context.publisher, context.audit)


async def test_status_change_is_broadcast(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    updated = await use_case(context).execute(
        dialog_id=dialog.id,
        actor_id=CLIENT_ID,
        request_status=RequestStatus.CLOSED,
    )

    assert updated.request_status is RequestStatus.CLOSED
    assert context.audit.events[0][0] == "dialog.status_changed"
    recipients, event = context.publisher.bulk_events[0]
    assert set(recipients) == {CLIENT_ID, dialog.participants[1].user_id}
    assert event["type"] == "dialog.status_changed"
    assert event["payload"]["request_status"] == "closed"


async def test_participants_can_be_replaced(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))
    new_participants = [
        Participant(user_id=CLIENT_ID, role=ParticipantRole.CLIENT),
        Participant(user_id=STRANGER_ID, role=ParticipantRole.OPERATOR),
    ]

    updated = await use_case(context).execute(
        dialog_id=dialog.id,
        actor_id=CLIENT_ID,
        participants=new_participants,
    )

    assert updated.participants == new_participants
    assert context.publisher.bulk_events == []


async def test_archive_flag_is_stored(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))

    await use_case(context).execute(
        dialog_id=dialog.id,
        actor_id=CLIENT_ID,
        archived=True,
    )

    assert context.states.archived == [(dialog.id, CLIENT_ID, True)]


async def test_missing_dialog_is_rejected(context: ChatTestContext) -> None:
    with pytest.raises(DialogNotFoundError):
        await use_case(context).execute(
            dialog_id=uuid4(),
            actor_id=CLIENT_ID,
            request_status=RequestStatus.CLOSED,
        )


async def test_no_changes_returns_existing_dialog() -> None:
    context = build_context(dialog=make_dialog())
    dialog = next(iter(context.dialogs.dialogs.values()))

    result = await use_case(context).execute(dialog_id=dialog.id, actor_id=CLIENT_ID)

    assert result is dialog
    assert context.publisher.bulk_events == []
    assert context.audit.events == []


async def test_stranger_cannot_update_dialog() -> None:
    context = build_context(dialog=make_dialog())
    dialog = next(iter(context.dialogs.dialogs.values()))

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(
            dialog_id=dialog.id,
            actor_id=STRANGER_ID,
            request_status=RequestStatus.CLOSED,
        )

    assert context.publisher.bulk_events == []
    assert context.audit.events == []
