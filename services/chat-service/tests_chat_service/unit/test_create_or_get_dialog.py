"""CreateOrGetDialog use-case tests."""

from __future__ import annotations

import pytest

from chat_service.application.ports.request_gateway import RequestInfo
from chat_service.application.use_cases.create_or_get_dialog import CreateOrGetDialog
from chat_service.domain.entities import Participant
from chat_service.domain.exceptions import NotAParticipantError
from chat_service.domain.value_objects import ParticipantRole, RequestStatus
from tests_chat_service.conftest import (
    CLIENT_ID,
    OPERATOR_ID,
    STRANGER_ID,
    ChatTestContext,
    build_context,
)


def participants() -> list[Participant]:
    return [
        Participant(user_id=CLIENT_ID, role=ParticipantRole.CLIENT),
        Participant(user_id=OPERATOR_ID, role=ParticipantRole.OPERATOR),
    ]


def use_case(context: ChatTestContext) -> CreateOrGetDialog:
    return CreateOrGetDialog(context.dialogs, context.gateway, context.audit)


async def test_dialog_is_created_once_per_request(context: ChatTestContext) -> None:
    first, first_created = await use_case(context).execute(
        actor_id=CLIENT_ID,
        request_id="req-42",
        participants=participants(),
    )
    second, second_created = await use_case(context).execute(
        actor_id=CLIENT_ID,
        request_id="req-42",
        participants=participants(),
    )

    assert first_created is True
    assert second_created is False
    assert first.id == second.id
    assert context.gateway.validated == ["req-42", "req-42"]
    assert len(context.audit.events) == 1
    assert context.audit.events[0][0] == "dialog.create"


async def test_external_gateway_can_override_status() -> None:
    context = build_context(
        request_info=RequestInfo(
            request_id="req-7",
            status=RequestStatus.CLOSED,
            patient_id=CLIENT_ID,
            doctor_id=OPERATOR_ID,
        )
    )

    dialog, created = await use_case(context).execute(
        actor_id=CLIENT_ID,
        request_id="req-7",
        participants=participants(),
        request_status=RequestStatus.OPEN,
    )

    assert created is True
    assert dialog.request_status is RequestStatus.CLOSED


async def test_default_status_is_open(context: ChatTestContext) -> None:
    dialog, _ = await use_case(context).execute(
        actor_id=CLIENT_ID,
        request_id="req-open",
        participants=participants(),
    )

    assert dialog.request_status is RequestStatus.OPEN


async def test_stranger_cannot_create_dialog_for_appointment() -> None:
    context = build_context(
        request_info=RequestInfo(
            request_id="req-7",
            status=RequestStatus.OPEN,
            patient_id=CLIENT_ID,
            doctor_id=OPERATOR_ID,
        )
    )

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(
            actor_id=STRANGER_ID,
            request_id="req-7",
            participants=participants(),
        )


async def test_creator_must_be_listed_as_participant() -> None:
    context = build_context(
        request_info=RequestInfo(
            request_id="req-8",
            status=RequestStatus.OPEN,
            patient_id=CLIENT_ID,
            doctor_id=OPERATOR_ID,
        )
    )

    with pytest.raises(NotAParticipantError):
        await use_case(context).execute(
            actor_id=CLIENT_ID,
            request_id="req-8",
            participants=[Participant(user_id=OPERATOR_ID, role=ParticipantRole.OPERATOR)],
        )
