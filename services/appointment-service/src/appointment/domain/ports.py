"""Ports (interfaces) required by the domain/application layers.

Adapters in ``appointment.infrastructure`` implement them; tests provide in-memory
fakes. Nothing here imports SQLAlchemy, Redis or RabbitMQ.
"""

from __future__ import annotations

from datetime import datetime
from typing import Protocol, Self
from uuid import UUID

from appointment.domain.entities import Appointment
from appointment.domain.enums import AppointmentStatus
from appointment.domain.events import DomainEvent


class AppointmentRepository(Protocol):
    async def add(self, appointment: Appointment) -> None: ...

    async def get(self, appointment_id: UUID) -> Appointment | None: ...

    async def update(self, appointment: Appointment) -> None: ...

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
    ) -> list[Appointment]: ...

    async def count(
        self,
        *,
        patient_id: UUID | None = None,
        doctor_id: UUID | None = None,
        status: AppointmentStatus | None = None,
        starts_after: datetime | None = None,
        starts_before: datetime | None = None,
    ) -> int: ...

    async def find_conflict(
        self,
        *,
        patient_id: UUID,
        doctor_id: UUID,
        starts_at: datetime,
        ends_at: datetime,
        exclude_id: UUID | None = None,
    ) -> Appointment | None:
        """Return an active appointment overlapping the window for either party."""
        ...

    async def lock_doctor(self, doctor_id: UUID) -> None:
        """Serialize concurrent bookings/reschedules of one doctor."""
        ...


class OutboxPort(Protocol):
    async def add(self, event: DomainEvent) -> None: ...


class UnitOfWork(Protocol):
    """Transaction boundary shared by repositories and the outbox."""

    appointments: AppointmentRepository
    outbox: OutboxPort

    async def commit(self) -> None: ...

    async def rollback(self) -> None: ...

    async def __aenter__(self) -> Self: ...

    async def __aexit__(self, exc_type: object, exc: object, tb: object) -> None: ...
