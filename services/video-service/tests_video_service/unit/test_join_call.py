"""Тесты выдачи токена на подключение."""

from __future__ import annotations

import uuid

import pytest

from tests_video_service.conftest import VideoTestContext, doctor_actor, patient_actor
from video_service.application.dto import CreateCallCommand, JoinCallCommand
from video_service.domain.errors import CallEndedError, ForbiddenError
from video_service.domain.value_objects import CallId


async def create_call(context: VideoTestContext) -> str:
    result = await context.container.create_call.execute(
        doctor_actor(),
        CreateCallCommand(appointment_id=uuid.UUID(int=1), patient_id="patient-1"),
    )
    return result.call.id


async def test_patient_gets_token_and_call_becomes_active(context: VideoTestContext) -> None:
    call_id = await create_call(context)

    result = await context.container.join_call.execute(
        patient_actor(),
        JoinCallCommand(call_id=CallId.parse(call_id), display_name="Иван"),
    )

    assert result.token == f"token.{result.call.room_name}.patient-1"
    assert result.livekit_url == "ws://livekit.test:7880"
    assert result.call.status == "active"
    assert result.call.started_at is not None
    assert result.expires_at > result.call.created_at


async def test_doctor_gets_token(context: VideoTestContext) -> None:
    call_id = await create_call(context)

    result = await context.container.join_call.execute(
        doctor_actor(),
        JoinCallCommand(call_id=CallId.parse(call_id)),
    )

    assert result.token.endswith(".doctor-1")


async def test_stranger_cannot_get_token(context: VideoTestContext) -> None:
    call_id = await create_call(context)

    with pytest.raises(ForbiddenError):
        await context.container.join_call.execute(
            patient_actor("stranger"),
            JoinCallCommand(call_id=CallId.parse(call_id)),
        )


async def test_ended_call_rejects_new_tokens(context: VideoTestContext) -> None:
    call_id = await create_call(context)
    await context.container.end_call.execute(doctor_actor(), CallId.parse(call_id))

    with pytest.raises(CallEndedError):
        await context.container.join_call.execute(
            patient_actor(),
            JoinCallCommand(call_id=CallId.parse(call_id)),
        )
