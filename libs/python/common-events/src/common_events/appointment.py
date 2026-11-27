"""Appointment-service events.

Producer: ``appointment-service``.
Exchange: ``appointment.events``.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from .base import EventPayload


class AppointmentEventTypes:
    CREATED = "appointment.appointment.created.v1"
    CONFIRMED = "appointment.appointment.confirmed.v1"
    CANCELLED = "appointment.appointment.cancelled.v1"
    RESCHEDULED = "appointment.appointment.rescheduled.v1"
    COMPLETED = "appointment.appointment.completed.v1"
    NO_SHOW = "appointment.appointment.no_show.v1"


class AppointmentCreatedPayload(EventPayload):
    appointment_id: UUID
    patient_id: UUID
    doctor_id: UUID
    starts_at: datetime
    ends_at: datetime


class AppointmentConfirmedPayload(EventPayload):
    appointment_id: UUID
    patient_id: UUID
    doctor_id: UUID
    starts_at: datetime
    ends_at: datetime


class AppointmentNoShowPayload(EventPayload):
    appointment_id: UUID
    patient_id: UUID
    doctor_id: UUID
    starts_at: datetime
    ends_at: datetime


class AppointmentCancelledPayload(EventPayload):
    appointment_id: UUID
    patient_id: UUID
    doctor_id: UUID
    reason: str | None = None


class AppointmentRescheduledPayload(EventPayload):
    appointment_id: UUID
    patient_id: UUID
    doctor_id: UUID
    previous_starts_at: datetime
    starts_at: datetime
    ends_at: datetime
    reason: str | None = None


class AppointmentCompletedPayload(EventPayload):
    appointment_id: UUID
    patient_id: UUID
    doctor_id: UUID
    completed_at: datetime


APPOINTMENT_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    AppointmentEventTypes.CREATED: AppointmentCreatedPayload,
    AppointmentEventTypes.CONFIRMED: AppointmentConfirmedPayload,
    AppointmentEventTypes.CANCELLED: AppointmentCancelledPayload,
    AppointmentEventTypes.RESCHEDULED: AppointmentRescheduledPayload,
    AppointmentEventTypes.COMPLETED: AppointmentCompletedPayload,
    AppointmentEventTypes.NO_SHOW: AppointmentNoShowPayload,
}
