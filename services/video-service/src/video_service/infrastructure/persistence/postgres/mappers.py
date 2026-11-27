"""Маппинг между доменной сущностью и ORM-моделью."""

from __future__ import annotations

from video_service.domain.entities import Call
from video_service.domain.enums import CallStatus
from video_service.domain.value_objects import CallId, RoomName
from video_service.infrastructure.persistence.postgres.models import CallModel


def to_model(call: Call) -> CallModel:
    return CallModel(
        id=call.id.value,
        appointment_id=call.appointment_id,
        room_name=call.room_name.value,
        doctor_id=call.doctor_id,
        patient_id=call.patient_id,
        status=call.status.value,
        max_participants=call.max_participants,
        created_at=call.created_at,
        started_at=call.started_at,
        ended_at=call.ended_at,
    )


def to_entity(model: CallModel) -> Call:
    return Call(
        id=CallId(model.id),
        appointment_id=model.appointment_id,
        room_name=RoomName(model.room_name),
        doctor_id=model.doctor_id,
        patient_id=model.patient_id,
        status=CallStatus(model.status),
        max_participants=model.max_participants,
        created_at=model.created_at,
        started_at=model.started_at,
        ended_at=model.ended_at,
    )
