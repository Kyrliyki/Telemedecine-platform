"""DeleteDialog use-case tests."""

from __future__ import annotations

from uuid import UUID, uuid4

import pytest

from chat_service.application.use_cases.delete_dialog import DeleteDialog
from chat_service.domain.exceptions import DialogNotFoundError, NotAParticipantError
from tests_chat_service.conftest import CLIENT_ID, STRANGER_ID, ChatTestContext


class RecordingChatEvents:
    def __init__(self) -> None:
        self.deleted: list[tuple[UUID, str]] = []

    async def publish_deleted(self, *, chat_id: UUID, request_id: str) -> None:
        self.deleted.append((chat_id, request_id))

    async def aclose(self) -> None:
        return None


def use_case(context: ChatTestContext, events: RecordingChatEvents) -> DeleteDialog:
    return DeleteDialog(context.dialogs, events, context.audit)


async def test_participant_deletes_dialog_and_publishes_event(
    context: ChatTestContext,
) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))
    events = RecordingChatEvents()

    await use_case(context, events).execute(dialog_id=dialog.id, actor_id=CLIENT_ID)

    assert dialog.id not in context.dialogs.dialogs
    assert events.deleted == [(dialog.id, dialog.request_id)]
    assert context.audit.events[0][0] == "dialog.delete"


async def test_stranger_cannot_delete_dialog(context: ChatTestContext) -> None:
    dialog = next(iter(context.dialogs.dialogs.values()))
    events = RecordingChatEvents()

    with pytest.raises(NotAParticipantError):
        await use_case(context, events).execute(
            dialog_id=dialog.id,
            actor_id=STRANGER_ID,
        )

    assert dialog.id in context.dialogs.dialogs
    assert events.deleted == []


async def test_missing_dialog_is_rejected(context: ChatTestContext) -> None:
    events = RecordingChatEvents()

    with pytest.raises(DialogNotFoundError):
        await use_case(context, events).execute(dialog_id=uuid4(), actor_id=CLIENT_ID)
