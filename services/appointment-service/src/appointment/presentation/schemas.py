"""Pydantic schemas for the HTTP API (separate from domain entities)."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from appointment.domain.entities import Appointment
from appointment.domain.enums import AppointmentStatus


class AppointmentCreateRequest(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        json_schema_extra={
            "example": {
                "doctor_id": "22222222-2222-2222-2222-222222222222",
                "starts_at": "2026-12-01T10:00:00Z",
                "ends_at": "2026-12-01T10:30:00Z",
                "reason": "Headache and high blood pressure for three days",
            }
        },
    )

    patient_id: UUID | None = Field(
        default=None,
        description="Required for doctors/admins; patients are always booked for themselves",
    )
    doctor_id: UUID
    starts_at: datetime = Field(description="ISO 8601 with timezone, e.g. 2026-12-01T10:00:00Z")
    ends_at: datetime = Field(description="ISO 8601 with timezone")
    reason: str = Field(min_length=3, max_length=500)


class AppointmentRescheduleRequest(BaseModel):
    starts_at: datetime
    ends_at: datetime


class AppointmentCancelRequest(BaseModel):
    reason: str = Field(min_length=3, max_length=500)


class AppointmentResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    patient_id: UUID
    doctor_id: UUID
    starts_at: datetime
    ends_at: datetime
    status: AppointmentStatus
    reason: str
    cancellation_reason: str | None
    created_at: datetime
    updated_at: datetime
    version: int

    @classmethod
    def from_entity(cls, appointment: Appointment) -> AppointmentResponse:
        return cls(
            id=appointment.id,
            patient_id=appointment.patient_id,
            doctor_id=appointment.doctor_id,
            starts_at=appointment.starts_at,
            ends_at=appointment.ends_at,
            status=appointment.status,
            reason=appointment.reason,
            cancellation_reason=appointment.cancellation_reason,
            created_at=appointment.created_at,
            updated_at=appointment.updated_at,
            version=appointment.version,
        )


class AppointmentListResponse(BaseModel):
    items: list[AppointmentResponse]
    total: int
    limit: int
    offset: int


class ErrorBody(BaseModel):
    code: str
    message: str
    request_id: str | None = None


class ErrorEnvelope(BaseModel):
    error: ErrorBody
