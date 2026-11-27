"""Тесты доменной модели звонка."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime, timedelta

import pytest

from video_service.domain.entities import Call
from video_service.domain.enums import ActorRole, CallStatus
from video_service.domain.errors import CallEndedError, ForbiddenError, ValidationError
from video_service.domain.value_objects import CallId

NOW = datetime(2026, 1, 15, 10, 0, tzinfo=UTC)


def make_call(**overrides) -> Call:
    params = {
        "call_id": CallId(uuid.UUID(int=1)),
        "appointment_id": uuid.UUID(int=2),
        "doctor_id": "doctor-1",
        "patient_id": "patient-1",
        "max_participants": 2,
        "now": NOW,
    }
    params.update(overrides)
    return Call.create(**params)


def test_create_call_builds_room_name_and_status() -> None:
    call = make_call()

    assert call.room_name.value == "call_00000000000000000000000000000001"
    assert call.status is CallStatus.CREATED
    assert call.started_at is None
    assert call.ended_at is None


def test_doctor_and_patient_must_differ() -> None:
    with pytest.raises(ValidationError):
        make_call(patient_id="doctor-1")


def test_max_participants_is_validated() -> None:
    with pytest.raises(ValidationError):
        make_call(max_participants=1)


def test_mark_started_is_idempotent() -> None:
    call = make_call()
    later = NOW + timedelta(minutes=1)

    assert call.mark_started(later) is True
    assert call.status is CallStatus.ACTIVE
    assert call.started_at == later
    assert call.mark_started(later + timedelta(minutes=1)) is False
    assert call.started_at == later


def test_end_is_idempotent_and_keeps_first_timestamp() -> None:
    call = make_call()
    ended_at = NOW + timedelta(minutes=30)

    assert call.end(ended_at) is True
    assert call.status is CallStatus.ENDED
    assert call.ended_at == ended_at
    assert call.end(ended_at + timedelta(minutes=5)) is False
    assert call.ended_at == ended_at


def test_participant_can_join_created_call() -> None:
    call = make_call()
    call.ensure_can_join("patient-1", frozenset({ActorRole.PATIENT}))


def test_stranger_cannot_join() -> None:
    call = make_call()
    with pytest.raises(ForbiddenError):
        call.ensure_can_join("stranger", frozenset({ActorRole.PATIENT}))


def test_admin_can_join_and_manage_any_call() -> None:
    call = make_call()
    admin = frozenset({ActorRole.ADMIN})

    call.ensure_can_join("admin-1", admin)
    call.ensure_can_manage("admin-1", admin)


def test_ended_call_cannot_be_joined() -> None:
    call = make_call()
    call.end(NOW)

    with pytest.raises(CallEndedError):
        call.ensure_can_join("patient-1", frozenset({ActorRole.PATIENT}))


def test_only_doctor_or_admin_can_manage() -> None:
    call = make_call()

    call.ensure_can_manage("doctor-1", frozenset({ActorRole.DOCTOR}))
    with pytest.raises(ForbiddenError):
        call.ensure_can_manage("patient-1", frozenset({ActorRole.PATIENT}))
