"""Доменные события. Публикуются после успешного изменения состояния."""

from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any


@dataclass(frozen=True, slots=True)
class DomainEvent:
    """Базовое доменное событие."""

    event_id: uuid.UUID = field(default_factory=uuid.uuid4)
    occurred_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @property
    def event_name(self) -> str:
        return type(self).__name__

    def to_payload(self) -> dict[str, Any]:
        raise NotImplementedError


@dataclass(frozen=True, slots=True)
class CallCreated(DomainEvent):
    call_id: str = ""
    appointment_id: str = ""
    room_name: str = ""
    doctor_id: str = ""
    patient_id: str = ""

    @property
    def event_name(self) -> str:
        return "video.call.created"

    def to_payload(self) -> dict[str, Any]:
        return {
            "event_id": str(self.event_id),
            "event_name": self.event_name,
            "occurred_at": self.occurred_at.isoformat(),
            "call_id": self.call_id,
            "appointment_id": self.appointment_id,
            "room_name": self.room_name,
            "doctor_id": self.doctor_id,
            "patient_id": self.patient_id,
        }


@dataclass(frozen=True, slots=True)
class CallEnded(DomainEvent):
    call_id: str = ""
    appointment_id: str = ""
    room_name: str = ""
    doctor_id: str = ""
    patient_id: str = ""
    ended_at: datetime | None = None

    @property
    def event_name(self) -> str:
        return "video.call.ended"

    def to_payload(self) -> dict[str, Any]:
        return {
            "event_id": str(self.event_id),
            "event_name": self.event_name,
            "occurred_at": self.occurred_at.isoformat(),
            "call_id": self.call_id,
            "appointment_id": self.appointment_id,
            "room_name": self.room_name,
            "doctor_id": self.doctor_id,
            "patient_id": self.patient_id,
            "ended_at": self.ended_at.isoformat() if self.ended_at else None,
        }
