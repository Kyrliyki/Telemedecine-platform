"""In-memory test doubles for the domain ports."""

from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import Any
from uuid import UUID

from appointment.domain.entities import Appointment
from appointment.domain.enums import BLOCKING_STATUSES, AppointmentStatus
from appointment.domain.errors import AppointmentNotFound, ConcurrencyConflict
from appointment.domain.events import DomainEvent

PATIENT_ID = UUID("11111111-1111-1111-1111-111111111111")
DOCTOR_ID = UUID("22222222-2222-2222-2222-222222222222")
ADMIN_ID = UUID("33333333-3333-3333-3333-333333333333")


@dataclass
class FakeState:
    appointments: dict[UUID, Appointment] = field(default_factory=dict)
    events: list[DomainEvent] = field(default_factory=list)


def _matches(
    appointment: Appointment,
    *,
    patient_id: UUID | None,
    doctor_id: UUID | None,
    status: AppointmentStatus | None,
    starts_after: datetime | None,
    starts_before: datetime | None,
) -> bool:
    if patient_id is not None and appointment.patient_id != patient_id:
        return False
    if doctor_id is not None and appointment.doctor_id != doctor_id:
        return False
    if status is not None and appointment.status != status:
        return False
    if starts_after is not None and appointment.starts_at < starts_after:
        return False
    return not (starts_before is not None and appointment.starts_at >= starts_before)


class InMemoryAppointmentRepository:
    def __init__(self, state: FakeState) -> None:
        self._state = state

    async def add(self, appointment: Appointment) -> None:
        self._state.appointments[appointment.id] = deepcopy(appointment)

    async def get(self, appointment_id: UUID) -> Appointment | None:
        appointment = self._state.appointments.get(appointment_id)
        return deepcopy(appointment) if appointment is not None else None

    async def update(self, appointment: Appointment) -> None:
        current = self._state.appointments.get(appointment.id)
        if current is None:
            raise AppointmentNotFound(f"appointment {appointment.id} was not found")
        if current.version != appointment.version - 1:
            raise ConcurrencyConflict("stale version")
        self._state.appointments[appointment.id] = deepcopy(appointment)

    async def list(
        self,
        *,
        patient_id: UUID | None = None,
        doctor_id: UUID | None = None,
        status: AppointmentStatus | None = None,
        starts_after: datetime | None = None,
        starts_before: datetime | None = None,
        limit: int = 20,
        offset: int = 0,
    ) -> list[Appointment]:
        items = [
            deepcopy(appointment)
            for appointment in self._state.appointments.values()
            if _matches(
                appointment,
                patient_id=patient_id,
                doctor_id=doctor_id,
                status=status,
                starts_after=starts_after,
                starts_before=starts_before,
            )
        ]
        items.sort(key=lambda item: item.starts_at)
        return items[offset : offset + limit]

    async def count(
        self,
        *,
        patient_id: UUID | None = None,
        doctor_id: UUID | None = None,
        status: AppointmentStatus | None = None,
        starts_after: datetime | None = None,
        starts_before: datetime | None = None,
    ) -> int:
        return sum(
            1
            for appointment in self._state.appointments.values()
            if _matches(
                appointment,
                patient_id=patient_id,
                doctor_id=doctor_id,
                status=status,
                starts_after=starts_after,
                starts_before=starts_before,
            )
        )

    async def find_conflict(
        self,
        *,
        patient_id: UUID,
        doctor_id: UUID,
        starts_at: datetime,
        ends_at: datetime,
        exclude_id: UUID | None = None,
    ) -> Appointment | None:
        for appointment in self._state.appointments.values():
            if appointment.status not in BLOCKING_STATUSES:
                continue
            if exclude_id is not None and appointment.id == exclude_id:
                continue
            if (
                (appointment.patient_id == patient_id or appointment.doctor_id == doctor_id)
                and appointment.starts_at < ends_at
                and appointment.ends_at > starts_at
            ):
                return deepcopy(appointment)
        return None

    async def lock_doctor(self, doctor_id: UUID) -> None:
        return None


class InMemoryOutbox:
    def __init__(self, state: FakeState) -> None:
        self._state = state

    async def add(self, event: DomainEvent) -> None:
        self._state.events.append(event)


class InMemoryUnitOfWork:
    def __init__(self, state: FakeState) -> None:
        self.appointments = InMemoryAppointmentRepository(state)
        self.outbox = InMemoryOutbox(state)
        self.commits = 0

    async def __aenter__(self) -> InMemoryUnitOfWork:
        return self

    async def __aexit__(self, exc_type: object, exc: object, tb: object) -> None:
        return None

    async def commit(self) -> None:
        self.commits += 1

    async def rollback(self) -> None:
        return None


class InMemoryCache:
    def __init__(self) -> None:
        self.data: dict[str, Any] = {}
        self.sets = 0
        self.deletes = 0

    async def get_json(self, key: str) -> Any | None:
        return deepcopy(self.data.get(key))

    async def set_json(self, key: str, value: Any, ttl_seconds: int | None = None) -> None:
        self.sets += 1
        self.data[key] = deepcopy(value)

    async def delete(self, key: str) -> None:
        self.deletes += 1
        self.data.pop(key, None)

    async def ping(self) -> bool:
        return True

    async def close(self) -> None:
        return None


class FixedClock:
    def __init__(self, now: datetime) -> None:
        self._now = now

    def now(self) -> datetime:
        return self._now

    def set(self, now: datetime) -> None:
        self._now = now

    def advance(self, **kwargs: float) -> None:
        from datetime import timedelta

        self._now += timedelta(**kwargs)


class RecordingPublisher:
    def __init__(self) -> None:
        self.events: list[DomainEvent] = []
        self.connect_calls = 0
        self.closed = False

    @property
    def connected(self) -> bool:
        return True

    async def connect(self) -> None:
        self.connect_calls += 1

    async def publish(self, event: DomainEvent) -> None:
        self.events.append(event)

    async def close(self) -> None:
        self.closed = True


def fixed_now() -> datetime:
    return datetime(2026, 11, 24, 9, 0, tzinfo=UTC)
