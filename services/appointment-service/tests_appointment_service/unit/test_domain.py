"""Domain unit tests: invariants and state transitions."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from uuid import uuid4

import pytest

from appointment.domain.access import Actor
from appointment.domain.entities import Appointment
from appointment.domain.enums import AppointmentStatus, Role
from appointment.domain.errors import (
    InvalidTransition,
    PermissionDenied,
    ValidationError,
)

NOW = datetime(2026, 11, 24, 9, 0, tzinfo=UTC)
START = NOW + timedelta(days=2)
END = START + timedelta(minutes=30)


def make_appointment(**overrides):
    params = {
        "patient_id": uuid4(),
        "doctor_id": uuid4(),
        "starts_at": START,
        "ends_at": END,
        "reason": "Regular checkup",
        "now": NOW,
    }
    params.update(overrides)
    return Appointment.schedule(**params)


def test_new_appointment_is_requested() -> None:
    appointment = make_appointment()
    assert appointment.status is AppointmentStatus.REQUESTED
    assert appointment.version == 1
    assert appointment.starts_at == START
    assert appointment.ends_at == END


def test_reason_is_normalised() -> None:
    appointment = make_appointment(reason="  Headache   after   lunch  ")
    assert appointment.reason == "Headache after lunch"


@pytest.mark.parametrize(
    "kwargs",
    [
        {"ends_at": START + timedelta(minutes=5)},
        {"ends_at": START + timedelta(hours=5)},
        {"ends_at": START},
        {"starts_at": START.replace(tzinfo=None)},
        {"reason": " x "},
    ],
)
def test_invalid_schedule_is_rejected(kwargs):
    with pytest.raises(ValidationError):
        make_appointment(**kwargs)


def test_confirm_twice_is_rejected() -> None:
    appointment = make_appointment()
    appointment.confirm(now=NOW)
    assert appointment.status is AppointmentStatus.CONFIRMED
    assert appointment.version == 2
    with pytest.raises(InvalidTransition):
        appointment.confirm(now=NOW)


def test_cancel_after_start_is_rejected() -> None:
    appointment = make_appointment()
    with pytest.raises(InvalidTransition):
        appointment.cancel(reason="changed my mind", now=END)


def test_cancel_sets_reason_and_is_terminal() -> None:
    appointment = make_appointment()
    appointment.cancel(reason="  going  abroad ", now=NOW)
    assert appointment.status is AppointmentStatus.CANCELLED
    assert appointment.cancellation_reason == "going abroad"
    with pytest.raises(InvalidTransition):
        appointment.cancel(reason="again", now=NOW)


def test_complete_requires_confirmation_and_start() -> None:
    appointment = make_appointment()
    with pytest.raises(InvalidTransition):
        appointment.complete(now=START + timedelta(minutes=1))

    appointment.confirm(now=NOW)
    with pytest.raises(InvalidTransition):
        appointment.complete(now=START - timedelta(minutes=1))

    appointment.complete(now=START + timedelta(minutes=1))
    assert appointment.status is AppointmentStatus.COMPLETED


def test_no_show_after_start() -> None:
    appointment = make_appointment()
    with pytest.raises(InvalidTransition):
        appointment.mark_no_show(now=NOW)

    appointment.mark_no_show(now=START + timedelta(minutes=1))
    assert appointment.status is AppointmentStatus.NO_SHOW


def test_reschedule_keeps_status_and_counts_version() -> None:
    appointment = make_appointment()
    appointment.confirm(now=NOW)
    new_start = START + timedelta(days=1)
    appointment.reschedule(starts_at=new_start, ends_at=new_start + timedelta(hours=1), now=NOW)
    assert appointment.status is AppointmentStatus.CONFIRMED
    assert appointment.starts_at == new_start
    assert appointment.version == 3


def test_patient_can_only_view_own_appointment() -> None:
    patient_id = uuid4()
    appointment = make_appointment(patient_id=patient_id)
    owner = Actor(id=patient_id, roles=frozenset({Role.PATIENT}))
    other = Actor(id=uuid4(), roles=frozenset({Role.PATIENT}))
    doctor = Actor(id=appointment.doctor_id, roles=frozenset({Role.DOCTOR}))

    owner.ensure_can_view(appointment)
    doctor.ensure_can_view(appointment)
    with pytest.raises(PermissionDenied):
        other.ensure_can_view(appointment)


def test_patient_cannot_confirm_or_book_for_someone_else() -> None:
    appointment = make_appointment()
    patient = Actor(id=appointment.patient_id, roles=frozenset({Role.PATIENT}))
    with pytest.raises(PermissionDenied):
        patient.ensure_can_manage_clinically(appointment)
    with pytest.raises(PermissionDenied):
        patient.ensure_can_book(patient_id=uuid4(), doctor_id=appointment.doctor_id)


def test_doctor_can_confirm_only_own_appointments() -> None:
    appointment = make_appointment()
    owner = Actor(id=appointment.doctor_id, roles=frozenset({Role.DOCTOR}))
    other = Actor(id=uuid4(), roles=frozenset({Role.DOCTOR}))
    owner.ensure_can_manage_clinically(appointment)
    with pytest.raises(PermissionDenied):
        other.ensure_can_manage_clinically(appointment)


def test_admin_can_manage_anything() -> None:
    appointment = make_appointment()
    admin = Actor(id=uuid4(), roles=frozenset({Role.ADMIN}))
    admin.ensure_can_view(appointment)
    admin.ensure_can_manage(appointment)
    admin.ensure_can_manage_clinically(appointment)


def test_patient_must_book_at_least_one_hour_ahead() -> None:
    patient = Actor(id=uuid4(), roles=frozenset({Role.PATIENT}))
    patient.ensure_can_schedule(
        starts_at=NOW + timedelta(hours=1), now=NOW, mode=Role.PATIENT
    )

    with pytest.raises(ValidationError, match="1 hour"):
        patient.ensure_can_schedule(
            starts_at=NOW + timedelta(minutes=59), now=NOW, mode=Role.PATIENT
        )
    with pytest.raises(ValidationError, match="1 hour"):
        patient.ensure_can_schedule(
            starts_at=NOW - timedelta(days=1), now=NOW, mode=Role.PATIENT
        )


def test_doctor_cannot_book_in_the_past_but_may_book_soon() -> None:
    doctor = Actor(id=uuid4(), roles=frozenset({Role.DOCTOR}))
    doctor.ensure_can_schedule(
        starts_at=NOW + timedelta(minutes=15), now=NOW, mode=Role.DOCTOR
    )
    doctor.ensure_can_schedule(starts_at=NOW, now=NOW, mode=Role.DOCTOR)

    with pytest.raises(ValidationError, match="past"):
        doctor.ensure_can_schedule(
            starts_at=NOW - timedelta(minutes=1), now=NOW, mode=Role.DOCTOR
        )


def test_admin_has_no_time_restrictions() -> None:
    admin = Actor(id=uuid4(), roles=frozenset({Role.ADMIN}))
    admin.ensure_can_schedule(
        starts_at=NOW - timedelta(days=30), now=NOW, mode=Role.ADMIN
    )
    admin.ensure_can_schedule(
        starts_at=NOW + timedelta(minutes=1), now=NOW, mode=Role.ADMIN
    )


def test_appointment_rejects_same_patient_and_doctor() -> None:
    user_id = uuid4()

    with pytest.raises(ValidationError, match="different users"):
        Appointment.schedule(
            patient_id=user_id,
            doctor_id=user_id,
            starts_at=START,
            ends_at=END,
            reason="Self appointment",
            now=NOW,
        )


def test_dual_role_uses_patient_lead_time_when_booking_for_self() -> None:
    dual = Actor(id=uuid4(), roles=frozenset({Role.DOCTOR, Role.PATIENT}))
    doctor_id = uuid4()

    mode = dual.booking_mode(patient_id=dual.id, doctor_id=doctor_id)

    assert mode is Role.PATIENT
    dual.ensure_can_schedule(
        starts_at=NOW + timedelta(hours=2), now=NOW, mode=mode
    )
    with pytest.raises(ValidationError, match="1 hour"):
        dual.ensure_can_schedule(
            starts_at=NOW + timedelta(minutes=30), now=NOW, mode=mode
        )


def test_dual_role_uses_doctor_lead_time_when_creating_for_patient() -> None:
    dual = Actor(id=uuid4(), roles=frozenset({Role.DOCTOR, Role.PATIENT}))
    patient_id = uuid4()

    mode = dual.booking_mode(patient_id=patient_id, doctor_id=dual.id)

    assert mode is Role.DOCTOR
    dual.ensure_can_schedule(starts_at=NOW, now=NOW, mode=mode)
    with pytest.raises(ValidationError, match="past"):
        dual.ensure_can_schedule(
            starts_at=NOW - timedelta(minutes=1), now=NOW, mode=mode
        )


def test_user_cannot_book_an_appointment_with_themselves() -> None:
    dual = Actor(id=uuid4(), roles=frozenset({Role.DOCTOR, Role.PATIENT}))

    with pytest.raises(ValidationError, match="different users"):
        dual.booking_mode(patient_id=dual.id, doctor_id=dual.id)
