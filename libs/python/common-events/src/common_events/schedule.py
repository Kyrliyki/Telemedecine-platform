"""Schedule-service events shared with Python services."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from .base import EventPayload


class ScheduleEventTypes:
    SLOT_RESERVED = "schedule.slot.reserved.v1"
    SLOT_CONFIRMED = "schedule.slot.confirmed.v1"
    SLOT_RELEASED = "schedule.slot.released.v1"
    SLOT_HOLD_EXPIRED = "schedule.slot.hold_expired.v1"
    AVAILABILITY_CHANGED = "schedule.availability.changed.v1"


class SlotReservedPayload(EventPayload):
    slot_id: UUID
    doctor_id: UUID
    appointment_id: UUID
    starts_at: datetime
    ends_at: datetime


class SlotReleasedPayload(EventPayload):
    slot_id: UUID
    doctor_id: UUID
    appointment_id: UUID
    starts_at: datetime
    released_at: datetime


class SlotConfirmedPayload(EventPayload):
    slot_id: UUID
    doctor_id: UUID
    appointment_id: UUID
    starts_at: datetime
    ends_at: datetime
    confirmed_at: datetime


class SlotHoldExpiredPayload(EventPayload):
    slot_id: UUID
    doctor_id: UUID
    appointment_id: UUID
    starts_at: datetime
    expired_at: datetime


class AvailabilityChangedPayload(EventPayload):
    doctor_id: UUID
    reason: str
    changed_at: datetime
    effective_from: datetime


SCHEDULE_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    ScheduleEventTypes.SLOT_RESERVED: SlotReservedPayload,
    ScheduleEventTypes.SLOT_CONFIRMED: SlotConfirmedPayload,
    ScheduleEventTypes.SLOT_RELEASED: SlotReleasedPayload,
    ScheduleEventTypes.SLOT_HOLD_EXPIRED: SlotHoldExpiredPayload,
    ScheduleEventTypes.AVAILABILITY_CHANGED: AvailabilityChangedPayload,
}
