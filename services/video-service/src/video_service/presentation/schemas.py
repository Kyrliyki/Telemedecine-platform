"""Pydantic-схемы HTTP API (контракт, независимый от домена)."""

from __future__ import annotations

import uuid
from datetime import datetime

from pydantic import BaseModel, Field

from video_service.application.dto import CallView
from video_service.domain.enums import CallStatus


class CreateCallRequest(BaseModel):
    appointment_id: uuid.UUID = Field(description="ID приёма из AppointmentService")
    patient_id: str = Field(min_length=1, max_length=128, description="ID пациента")
    doctor_id: str | None = Field(
        default=None,
        max_length=128,
        description="Заполняется только администратором (иначе берётся из JWT)",
    )
    max_participants: int = Field(default=2, ge=2, le=100)


class JoinCallRequest(BaseModel):
    display_name: str | None = Field(default=None, max_length=200)


class CallResponse(BaseModel):
    id: uuid.UUID
    appointment_id: uuid.UUID
    room_name: str
    doctor_id: str
    patient_id: str
    status: CallStatus
    max_participants: int
    created_at: datetime
    started_at: datetime | None
    ended_at: datetime | None

    @classmethod
    def from_view(cls, call: CallView) -> CallResponse:
        return cls(
            id=uuid.UUID(call.id),
            appointment_id=uuid.UUID(call.appointment_id),
            room_name=call.room_name,
            doctor_id=call.doctor_id,
            patient_id=call.patient_id,
            status=call.status,
            max_participants=call.max_participants,
            created_at=call.created_at,
            started_at=call.started_at,
            ended_at=call.ended_at,
        )


class JoinCallResponse(BaseModel):
    call: CallResponse
    token: str = Field(description="JWT для подключения к комнате LiveKit")
    livekit_url: str = Field(description="WebSocket URL LiveKit для клиента")
    expires_at: datetime


class CallListResponse(BaseModel):
    items: list[CallResponse]
    limit: int
    offset: int


class ErrorBody(BaseModel):
    code: str
    message: str
    request_id: str | None = None


class ErrorResponse(BaseModel):
    error: ErrorBody


class HealthResponse(BaseModel):
    status: str
    checks: dict[str, bool] | None = None
