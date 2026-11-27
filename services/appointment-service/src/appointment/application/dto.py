"""DTOs shared by use cases, cache serialisation and the API layer."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any
from uuid import UUID

from appointment.domain.entities import Appointment
from appointment.domain.enums import AppointmentStatus


@dataclass(frozen=True, slots=True)
class ListAppointmentsQuery:
    status: AppointmentStatus | None = None
    patient_id: UUID | None = None
    doctor_id: UUID | None = None
    view: str | None = None
    starts_after: datetime | None = None
    starts_before: datetime | None = None
    limit: int = 20
    offset: int = 0


def appointment_to_dict(appointment: Appointment) -> dict[str, Any]:
    """Serialise an entity for Redis."""
    return {
        "id": str(appointment.id),
        "patient_id": str(appointment.patient_id),
        "doctor_id": str(appointment.doctor_id),
        "starts_at": appointment.starts_at.isoformat(),
        "ends_at": appointment.ends_at.isoformat(),
        "status": appointment.status.value,
        "reason": appointment.reason,
        "created_at": appointment.created_at.isoformat(),
        "updated_at": appointment.updated_at.isoformat(),
        "version": appointment.version,
        "cancellation_reason": appointment.cancellation_reason,
    }


def appointment_from_dict(data: dict[str, Any]) -> Appointment:
    """Restore an entity from Redis."""
    return Appointment(
        id=UUID(data["id"]),
        patient_id=UUID(data["patient_id"]),
        doctor_id=UUID(data["doctor_id"]),
        starts_at=datetime.fromisoformat(data["starts_at"]),
        ends_at=datetime.fromisoformat(data["ends_at"]),
        status=AppointmentStatus(data["status"]),
        reason=data["reason"],
        created_at=datetime.fromisoformat(data["created_at"]),
        updated_at=datetime.fromisoformat(data["updated_at"]),
        version=int(data["version"]),
        cancellation_reason=data.get("cancellation_reason"),
    )


def cache_key(appointment_id: UUID) -> str:
    return f"appointment:v1:{appointment_id}"
