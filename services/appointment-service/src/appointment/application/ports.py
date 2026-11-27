"""Application-level ports: cache, clock and event publisher."""

from __future__ import annotations

from datetime import datetime
from typing import Any, Protocol
from uuid import UUID

from appointment.domain.events import DomainEvent


class Cache(Protocol):
    async def get_json(self, key: str) -> Any | None: ...

    async def set_json(self, key: str, value: Any, ttl_seconds: int | None = None) -> None: ...

    async def delete(self, key: str) -> None: ...

    async def ping(self) -> bool: ...

    async def close(self) -> None: ...


class Clock(Protocol):
    def now(self) -> datetime: ...


class EventPublisher(Protocol):
    async def connect(self) -> None: ...

    async def publish(self, event: DomainEvent) -> None: ...

    async def close(self) -> None: ...

    @property
    def connected(self) -> bool: ...


class ScheduleGateway(Protocol):
    async def hold(
        self,
        *,
        appointment_id: UUID,
        doctor_id: UUID,
        patient_id: UUID,
        starts_at: datetime,
    ) -> None: ...

    async def confirm(self, *, appointment_id: UUID) -> None: ...

    async def release(self, *, appointment_id: UUID) -> None: ...

    async def aclose(self) -> None: ...


class UserDirectoryGateway(Protocol):
    async def require_patient(self, patient_id: UUID) -> None: ...

    async def require_doctor(self, doctor_id: UUID) -> None: ...

    async def aclose(self) -> None: ...
