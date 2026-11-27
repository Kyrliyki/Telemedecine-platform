"""Domain events emitted by the appointment aggregate.

Events are stored in the transactional outbox and later published to RabbitMQ by a
background relay. Payloads stay snake_case in the outbox for readability; the
canonical ``common-events`` envelope converts them to camelCase on the wire.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any
from uuid import UUID, uuid4

from common_events import APPOINTMENT_EVENT_PAYLOADS, EventEnvelope

from appointment.domain.entities import Appointment

EVENT_CREATED = "appointment.appointment.created.v1"
EVENT_CONFIRMED = "appointment.appointment.confirmed.v1"
EVENT_RESCHEDULED = "appointment.appointment.rescheduled.v1"
EVENT_CANCELLED = "appointment.appointment.cancelled.v1"
EVENT_COMPLETED = "appointment.appointment.completed.v1"
EVENT_NO_SHOW = "appointment.appointment.no_show.v1"


@dataclass(frozen=True, slots=True)
class DomainEvent:
    """Something that happened in the domain, ready for the outbox."""

    event_type: str
    aggregate_id: UUID
    payload: dict[str, Any]
    event_id: UUID = field(default_factory=uuid4)
    occurred_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    @property
    def routing_key(self) -> str:
        return self.event_type

    def envelope(self) -> EventEnvelope:
        """Build the canonical wire envelope shared with Java consumers."""
        payload_model = APPOINTMENT_EVENT_PAYLOADS[self.event_type]
        validated = payload_model.model_validate(self.payload)
        return EventEnvelope(
            event_id=self.event_id,
            event_type=self.event_type,
            occurred_at=self.occurred_at,
            payload=validated.to_payload(),
        )


def _snapshot(appointment: Appointment) -> dict[str, Any]:
    return {
        "appointment_id": str(appointment.id),
        "patient_id": str(appointment.patient_id),
        "doctor_id": str(appointment.doctor_id),
        "starts_at": appointment.starts_at.isoformat(),
        "ends_at": appointment.ends_at.isoformat(),
    }


def appointment_requested(appointment: Appointment, *, now: datetime) -> DomainEvent:
    return DomainEvent(
        event_type=EVENT_CREATED,
        aggregate_id=appointment.id,
        payload=_snapshot(appointment),
        occurred_at=now,
    )


def appointment_confirmed(appointment: Appointment, *, now: datetime) -> DomainEvent:
    return DomainEvent(
        event_type=EVENT_CONFIRMED,
        aggregate_id=appointment.id,
        payload=_snapshot(appointment),
        occurred_at=now,
    )


def appointment_rescheduled(
    appointment: Appointment,
    *,
    previous_starts_at: datetime,
    previous_ends_at: datetime,
    now: datetime,
) -> DomainEvent:
    return DomainEvent(
        event_type=EVENT_RESCHEDULED,
        aggregate_id=appointment.id,
        payload={
            **_snapshot(appointment),
            "previous_starts_at": previous_starts_at.isoformat(),
            "previous_ends_at": previous_ends_at.isoformat(),
            "reason": appointment.reason,
        },
        occurred_at=now,
    )


def appointment_cancelled(appointment: Appointment, *, now: datetime) -> DomainEvent:
    return DomainEvent(
        event_type=EVENT_CANCELLED,
        aggregate_id=appointment.id,
        payload={
            **_snapshot(appointment),
            "reason": appointment.cancellation_reason,
        },
        occurred_at=now,
    )


def appointment_completed(appointment: Appointment, *, now: datetime) -> DomainEvent:
    return DomainEvent(
        event_type=EVENT_COMPLETED,
        aggregate_id=appointment.id,
        payload={
            **_snapshot(appointment),
            "completed_at": now.isoformat(),
        },
        occurred_at=now,
    )


def appointment_no_show(appointment: Appointment, *, now: datetime) -> DomainEvent:
    return DomainEvent(
        event_type=EVENT_NO_SHOW,
        aggregate_id=appointment.id,
        payload=_snapshot(appointment),
        occurred_at=now,
    )
