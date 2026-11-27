"""Тесты use case создания звонка."""

from __future__ import annotations

import uuid

import pytest

from tests_video_service.conftest import (
    FailingUnitOfWork,
    VideoTestContext,
    admin_actor,
    build_test_context,
    doctor_actor,
    patient_actor,
)
from video_service.application.dto import CreateCallCommand
from video_service.application.ports import AppointmentInfo
from video_service.domain.errors import (
    AppointmentNotConfirmedError,
    AppointmentNotFoundError,
    ForbiddenError,
    ValidationError,
)
from video_service.domain.events import CallCreated
from video_service.domain.value_objects import CallId

APPOINTMENT_ID = uuid.UUID(int=42)
PATIENT_ID = uuid.UUID(int=7)
DOCTOR_ID = uuid.UUID(int=8)

class FakeAppointments:
    def __init__(self, info: AppointmentInfo | None) -> None:
        self._info = info

    async def get(self, appointment_id: uuid.UUID) -> AppointmentInfo | None:
        return self._info

def appointment(status: str = "CONFIRMED") -> AppointmentInfo:
    return AppointmentInfo(
        appointment_id=APPOINTMENT_ID,
        patient_id=PATIENT_ID,
        doctor_id=DOCTOR_ID,
        status=status,
    )

def command(**overrides) -> CreateCallCommand:
    params = {
        "appointment_id": APPOINTMENT_ID,
        "patient_id": "patient-1",
    }
    params.update(overrides)
    return CreateCallCommand(**params)

async def test_doctor_creates_call_and_livekit_room(context: VideoTestContext) -> None:
    result = await context.container.create_call.execute(doctor_actor(), command())

    assert result.created is True
    assert result.call.doctor_id == "doctor-1"
    assert result.call.patient_id == "patient-1"
    assert result.call.status == "created"
    assert len(context.livekit.created_rooms) == 1
    assert context.livekit.created_rooms[0]["room_name"].value == result.call.room_name
    assert context.livekit.created_rooms[0]["metadata"]["appointment_id"] == str(APPOINTMENT_ID)

    stored = await context.container.get_call.execute(
        doctor_actor(),
        CallId.parse(result.call.id),
    )
    assert stored.id == result.call.id

    assert len(context.publisher.events) == 1
    event = context.publisher.events[0]
    assert isinstance(event, CallCreated)
    assert event.call_id == result.call.id

async def test_create_is_idempotent_by_appointment(context: VideoTestContext) -> None:
    first = await context.container.create_call.execute(doctor_actor(), command())
    second = await context.container.create_call.execute(doctor_actor(), command())

    assert first.created is True
    assert second.created is False
    assert first.call.id == second.call.id
    assert len(context.livekit.created_rooms) == 1
    assert len(context.publisher.events) == 1

    
    assert len(context.notifier.calls) == 1

async def test_new_call_is_created_after_previous_ended(
    context: VideoTestContext,
) -> None:
    first = await context.container.create_call.execute(doctor_actor(), command())
    await context.container.end_call.execute(
        doctor_actor(),
        CallId.parse(first.call.id),
    )

    second = await context.container.create_call.execute(doctor_actor(), command())

    assert second.created is True
    assert second.call.id != first.call.id
    assert len(context.livekit.created_rooms) == 2
    assert len(context.livekit.deleted_rooms) == 1
    assert len(context.notifier.calls) == 2

async def test_doctor_creation_notifies_patient(context: VideoTestContext) -> None:
    result = await context.container.create_call.execute(doctor_actor(), command())

    assert len(context.notifier.calls) == 1
    notification = context.notifier.calls[0]
    assert notification.id == result.call.id
    assert notification.patient_id == "patient-1"
    assert notification.appointment_id == str(APPOINTMENT_ID)

async def test_idempotent_replay_does_not_leak_foreign_call(
    context: VideoTestContext,
) -> None:
    await context.container.create_call.execute(doctor_actor("doctor-1"), command())

    with pytest.raises(ForbiddenError):
        await context.container.create_call.execute(doctor_actor("doctor-2"), command())

async def test_patient_cannot_create_call(context: VideoTestContext) -> None:
    with pytest.raises(ForbiddenError):
        await context.container.create_call.execute(patient_actor(), command())

async def test_doctor_cannot_spoof_another_doctor(context: VideoTestContext) -> None:
    with pytest.raises(ForbiddenError):
        await context.container.create_call.execute(
            doctor_actor("doctor-1"),
            command(doctor_id="doctor-2"),
        )

async def test_admin_must_specify_doctor(context: VideoTestContext) -> None:
    with pytest.raises(ValidationError):
        await context.container.create_call.execute(admin_actor(), command())

    result = await context.container.create_call.execute(
        admin_actor(),
        command(doctor_id="doctor-7"),
    )
    assert result.call.doctor_id == "doctor-7"

async def test_patient_and_doctor_must_differ(context: VideoTestContext) -> None:
    with pytest.raises(ValidationError):
        await context.container.create_call.execute(
            doctor_actor(),
            command(patient_id="doctor-1"),
        )

async def test_room_is_deleted_when_database_fails() -> None:
    context = build_test_context(uow_factory=lambda: FailingUnitOfWork())

    with pytest.raises(RuntimeError):
        await context.container.create_call.execute(doctor_actor(), command())

    assert len(context.livekit.created_rooms) == 1
    assert len(context.livekit.deleted_rooms) == 1

async def test_confirmed_appointment_is_accepted(context: VideoTestContext) -> None:
    context.container.create_call._appointments = FakeAppointments(appointment("CONFIRMED"))

    result = await context.container.create_call.execute(
        doctor_actor(str(DOCTOR_ID)),
        command(patient_id=str(PATIENT_ID)),
    )

    assert result.created is True

async def test_requested_appointment_is_rejected(context: VideoTestContext) -> None:
    context.container.create_call._appointments = FakeAppointments(appointment("REQUESTED"))

    with pytest.raises(AppointmentNotConfirmedError):
        await context.container.create_call.execute(
            doctor_actor(str(DOCTOR_ID)),
            command(patient_id=str(PATIENT_ID)),
        )

async def test_missing_appointment_is_rejected(context: VideoTestContext) -> None:
    context.container.create_call._appointments = FakeAppointments(None)

    with pytest.raises(AppointmentNotFoundError):
        await context.container.create_call.execute(
            doctor_actor(str(DOCTOR_ID)),
            command(patient_id=str(PATIENT_ID)),
        )

async def test_foreign_patient_is_rejected(context: VideoTestContext) -> None:
    context.container.create_call._appointments = FakeAppointments(appointment("CONFIRMED"))

    with pytest.raises(ForbiddenError):
        await context.container.create_call.execute(
            doctor_actor(str(DOCTOR_ID)),
            command(patient_id=str(uuid.UUID(int=99))),
        )
