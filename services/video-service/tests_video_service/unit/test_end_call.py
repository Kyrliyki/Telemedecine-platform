"""Тесты завершения звонка."""

from __future__ import annotations

import uuid

import pytest

from tests_video_service.conftest import VideoTestContext, doctor_actor, patient_actor
from video_service.application.dto import CreateCallCommand
from video_service.domain.errors import ForbiddenError
from video_service.domain.events import CallEnded
from video_service.domain.value_objects import CallId


async def create_call(context: VideoTestContext) -> str:
    result = await context.container.create_call.execute(
        doctor_actor(),
        CreateCallCommand(appointment_id=uuid.UUID(int=1), patient_id="patient-1"),
    )
    return result.call.id


async def test_doctor_ends_call_and_room_is_deleted(context: VideoTestContext) -> None:
    call_id = await create_call(context)

    result = await context.container.end_call.execute(
        doctor_actor(),
        CallId.parse(call_id),
    )

    assert result.status == "ended"
    assert result.ended_at is not None
    assert len(context.livekit.deleted_rooms) == 1
    event = context.publisher.events[-1]
    assert isinstance(event, CallEnded)
    assert event.patient_id == "patient-1"
    assert event.doctor_id == "doctor-1"


async def test_end_is_idempotent(context: VideoTestContext) -> None:
    call_id = await create_call(context)

    first = await context.container.end_call.execute(doctor_actor(), CallId.parse(call_id))
    second = await context.container.end_call.execute(doctor_actor(), CallId.parse(call_id))

    assert first.ended_at == second.ended_at
    assert len(context.livekit.deleted_rooms) == 1
    assert len([e for e in context.publisher.events if isinstance(e, CallEnded)]) == 1


async def test_patient_cannot_end_call(context: VideoTestContext) -> None:
    call_id = await create_call(context)

    with pytest.raises(ForbiddenError):
        await context.container.end_call.execute(patient_actor(), CallId.parse(call_id))


async def test_livekit_delete_failure_does_not_cancel_ending(context: VideoTestContext) -> None:
    call_id = await create_call(context)
    context.livekit.fail_delete = True

    result = await context.container.end_call.execute(doctor_actor(), CallId.parse(call_id))

    assert result.status == "ended"
