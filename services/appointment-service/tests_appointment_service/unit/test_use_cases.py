"""Application/use-case tests with in-memory adapters."""

from __future__ import annotations

from datetime import timedelta
from uuid import UUID

import pytest
from pydantic import ValidationError as PydanticValidationError

from appointment.application.dto import ListAppointmentsQuery
from appointment.domain.access import Actor
from appointment.domain.enums import AppointmentStatus, Role
from appointment.domain.errors import (
    OverlappingAppointment,
    PermissionDenied,
    ValidationError,
)
from appointment.infrastructure.config import Settings
from tests_appointment_service.fakes import DOCTOR_ID, PATIENT_ID, InMemoryCache

START_OFFSET = timedelta(days=3)


def start_and_end(clock):
    start = clock.now() + START_OFFSET
    return start, start + timedelta(minutes=30)


async def create(container, clock, *, patient_id=PATIENT_ID, doctor_id=DOCTOR_ID):
    start, end = start_and_end(clock)
    return await container.create_appointment(
        actor=Actor(id=patient_id, roles=frozenset({Role.PATIENT})),
        patient_id=patient_id,
        doctor_id=doctor_id,
        starts_at=start,
        ends_at=end,
        reason="Chest pain",
    )


async def test_create_appointment_writes_outbox_event(state, container, clock):
    appointment = await create(container, clock)

    assert appointment.status is AppointmentStatus.REQUESTED
    assert state.appointments[appointment.id].version == 1
    assert len(state.events) == 1
    event = state.events[0]
    assert event.event_type == "appointment.appointment.created.v1"
    assert event.payload["appointment_id"] == str(appointment.id)


async def test_overlap_is_rejected(container, clock):
    await create(container, clock)

    start, end = start_and_end(clock)
    with pytest.raises(OverlappingAppointment):
        await container.create_appointment(
            actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
            patient_id=PATIENT_ID,
            doctor_id=DOCTOR_ID,
            starts_at=start + timedelta(minutes=15),
            ends_at=end + timedelta(minutes=15),
            reason="Second opinion",
        )


async def test_adjacent_appointments_are_allowed(container, clock):
    first = await create(container, clock)
    second = await container.create_appointment(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
        patient_id=PATIENT_ID,
        doctor_id=DOCTOR_ID,
        starts_at=first.ends_at,
        ends_at=first.ends_at + timedelta(minutes=30),
        reason="Follow-up",
    )
    assert second.id != first.id


async def test_get_appointment_uses_cache(container, clock, cache: InMemoryCache):
    appointment = await create(container, clock)

    first = await container.get_appointment(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})), appointment_id=appointment.id
    )
    second = await container.get_appointment(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})), appointment_id=appointment.id
    )

    assert first.id == second.id
    assert cache.sets == 1
    assert len(cache.data) == 1


async def test_touching_an_appointment_invalidates_cache(container, clock, cache):
    appointment = await create(container, clock)
    await container.get_appointment(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})), appointment_id=appointment.id
    )
    assert cache.data

    await container.confirm_appointment(
        actor=Actor(id=DOCTOR_ID, roles=frozenset({Role.DOCTOR})), appointment_id=appointment.id
    )
    assert cache.data == {}
    assert cache.deletes == 1


async def test_patient_cannot_confirm(container, clock):
    appointment = await create(container, clock)
    with pytest.raises(PermissionDenied):
        await container.confirm_appointment(
            actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
            appointment_id=appointment.id,
        )


async def test_reschedule_detects_conflicts(container, clock):
    first = await create(container, clock)
    second = await container.create_appointment(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
        patient_id=PATIENT_ID,
        doctor_id=DOCTOR_ID,
        starts_at=first.ends_at,
        ends_at=first.ends_at + timedelta(minutes=30),
        reason="Follow-up",
    )

    with pytest.raises(OverlappingAppointment):
        await container.reschedule_appointment(
            actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
            appointment_id=second.id,
            starts_at=first.starts_at,
            ends_at=first.ends_at,
        )


async def test_list_scopes_patient_to_own_appointments(container, clock):
    await create(container, clock)
    other_id = UUID("99999999-9999-9999-9999-999999999999")
    start, end = start_and_end(clock)
    await container.create_appointment(
        actor=Actor(id=other_id, roles=frozenset({Role.PATIENT})),
        patient_id=other_id,
        doctor_id=DOCTOR_ID,
        starts_at=start + timedelta(hours=2),
        ends_at=end + timedelta(hours=2),
        reason="Migraine",
    )

    items, total = await container.list_appointments(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
        query=ListAppointmentsQuery(),
    )

    assert total == 1
    assert items[0].patient_id == PATIENT_ID

    with pytest.raises(PermissionDenied):
        await container.list_appointments(
            actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
            query=ListAppointmentsQuery(patient_id=other_id),
        )


async def test_create_requires_future_slot(container, clock):
    with pytest.raises(ValidationError):
        await container.create_appointment(
            actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
            patient_id=PATIENT_ID,
            doctor_id=DOCTOR_ID,
            starts_at=clock.now() - timedelta(hours=1),
            ends_at=clock.now() + timedelta(hours=1),
            reason="Retroactive booking",
        )


async def test_complete_after_start(container, clock, state):
    appointment = await create(container, clock)
    await container.confirm_appointment(
        actor=Actor(id=DOCTOR_ID, roles=frozenset({Role.DOCTOR})), appointment_id=appointment.id
    )
    clock.set(appointment.starts_at + timedelta(minutes=5))
    completed = await container.complete_appointment(
        actor=Actor(id=DOCTOR_ID, roles=frozenset({Role.DOCTOR})), appointment_id=appointment.id
    )
    assert completed.status is AppointmentStatus.COMPLETED
    assert [event.event_type for event in state.events] == [
        "appointment.appointment.created.v1",
        "appointment.appointment.confirmed.v1",
        "appointment.appointment.completed.v1",
    ]


async def test_cancel_records_reason(container, clock):
    appointment = await create(container, clock)
    cancelled = await container.cancel_appointment(
        actor=Actor(id=PATIENT_ID, roles=frozenset({Role.PATIENT})),
        appointment_id=appointment.id,
        reason="Feeling better",
    )
    assert cancelled.status is AppointmentStatus.CANCELLED
    assert cancelled.cancellation_reason == "Feeling better"


def test_settings_do_not_allow_default_jwt_secret_in_production():
    from appointment.infrastructure.config import DEFAULT_JWT_SECRET

    with pytest.raises(PydanticValidationError):
        Settings(env="prod", jwt_secret=DEFAULT_JWT_SECRET)
