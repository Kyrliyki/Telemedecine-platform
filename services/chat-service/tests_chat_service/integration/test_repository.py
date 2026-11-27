"""PostgreSQL integration tests for chat repositories (Testcontainers)."""

from __future__ import annotations

import asyncio
from uuid import uuid4

import pytest

from chat_service.domain.entities import Participant
from chat_service.domain.value_objects import ParticipantRole, RequestStatus
from chat_service.infrastructure.db.repositories import (
    SqlDialogRepository,
    SqlDialogStateRepository,
    SqlMessageRepository,
)

pytestmark = pytest.mark.integration


def _participants() -> list[Participant]:
    return [
        Participant(user_id=uuid4(), role=ParticipantRole.CLIENT),
        Participant(user_id=uuid4(), role=ParticipantRole.OPERATOR),
    ]


async def _create_dialog(session_factory, request_id: str = "req-integration"):
    participants = _participants()
    async with session_factory() as session:
        dialog, created = await SqlDialogRepository(session).create_idempotent(
            request_id, participants, RequestStatus.OPEN
        )
    assert created is True
    return dialog, participants


async def test_dialog_creation_is_idempotent_and_initializes_state(session_factory) -> None:
    participants = _participants()

    async with session_factory() as session:
        repository = SqlDialogRepository(session)
        dialog, created = await repository.create_idempotent(
            "req-idempotent", participants, RequestStatus.OPEN
        )
        assert created is True
        assert dialog.request_id == "req-idempotent"
        assert {p.user_id for p in dialog.participants} == {p.user_id for p in participants}
        assert await repository.is_participant(dialog.id, participants[0].user_id) is True

    async with session_factory() as session:
        again, created_again = await SqlDialogRepository(session).create_idempotent(
            "req-idempotent", participants, RequestStatus.OPEN
        )
        assert created_again is False
        assert again.id == dialog.id

    async with session_factory() as session:
        state = await SqlDialogStateRepository(session).get(dialog.id, participants[0].user_id)
        assert state is not None
        assert state.unread_count == 0
        assert state.archived is False


async def test_message_save_is_idempotent_by_client_message_id(session_factory) -> None:
    dialog, participants = await _create_dialog(session_factory, "req-messages")
    client_message_id = uuid4()
    author_id = participants[0].user_id

    async with session_factory() as session:
        repository = SqlMessageRepository(session)
        first, created = await repository.save(
            message_id=uuid4(),
            dialog_id=dialog.id,
            author_id=author_id,
            content="hello",
            client_msg_id=client_message_id,
        )
        assert created is True

        duplicate, created_again = await repository.save(
            message_id=uuid4(),
            dialog_id=dialog.id,
            author_id=author_id,
            content="hello",
            client_msg_id=client_message_id,
        )
        assert created_again is False
        assert duplicate.id == first.id

        page, next_cursor = await repository.page(dialog.id, cursor=None, limit=10)
        assert [message.id for message in page] == [first.id]
        assert next_cursor is None

        await repository.soft_delete(first.id)

    async with session_factory() as session:
        page, _ = await SqlMessageRepository(session).page(dialog.id, cursor=None, limit=10)
        assert page == []


async def test_message_history_pagination(session_factory) -> None:
    dialog, participants = await _create_dialog(session_factory, "req-pagination")

    async with session_factory() as session:
        repository = SqlMessageRepository(session)
        for index in range(3):
            await repository.save(
                message_id=uuid4(),
                dialog_id=dialog.id,
                author_id=participants[0].user_id,
                content=f"message-{index}",
                client_msg_id=uuid4(),
            )
            await asyncio.sleep(0.01)

        first_page, cursor = await repository.page(dialog.id, cursor=None, limit=2)
        second_page, _ = await repository.page(dialog.id, cursor=cursor, limit=2)

    assert [message.content for message in first_page] == ["message-2", "message-1"]
    assert [message.content for message in second_page] == ["message-0"]
    assert cursor is not None


async def test_state_read_archive_and_unread(session_factory) -> None:
    dialog, participants = await _create_dialog(session_factory, "req-state")
    client, operator = participants[0].user_id, participants[1].user_id
    message_id = uuid4()

    async with session_factory() as session:
        states = SqlDialogStateRepository(session)
        await states.increment_unread(dialog.id, exclude_user_id=client)

    async with session_factory() as session:
        states = SqlDialogStateRepository(session)
        state = await states.get(dialog.id, operator)
        assert state is not None and state.unread_count == 1
        assert (await states.get(dialog.id, client)).unread_count == 0

        await states.mark_read(dialog.id, operator, message_id)
        state = await states.get(dialog.id, operator)
        assert state.unread_count == 0
        assert state.last_read_message_id == message_id

        await states.set_archived(dialog.id, operator, True)
        state = await states.get(dialog.id, operator)
        assert state.archived is True


async def test_dialog_listing_status_and_delete(session_factory) -> None:
    dialog, participants = await _create_dialog(session_factory, "req-listing")

    async with session_factory() as session:
        repository = SqlDialogRepository(session)
        listed, _ = await repository.list_for_user(
            participants[0].user_id,
            cursor=None,
            limit=10,
            archived=None,
            request_status=None,
        )
        assert [item.id for item in listed] == [dialog.id]

        updated = await repository.update_status(dialog.id, RequestStatus.CLOSED)
        assert updated.request_status == RequestStatus.CLOSED

        assert await repository.delete(dialog.id) is True
        assert await repository.get(dialog.id) is None
