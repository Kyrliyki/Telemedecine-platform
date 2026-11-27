"""Порты доменного слоя (реализуются инфраструктурой)."""

from __future__ import annotations

import uuid
from typing import Protocol

from video_service.domain.entities import Call
from video_service.domain.enums import CallStatus
from video_service.domain.value_objects import CallId, RoomName


class CallRepository(Protocol):
    """Хранилище звонков. Реализации: PostgreSQL, in-memory."""

    async def get(self, call_id: CallId) -> Call | None: ...

    async def get_current_by_appointment(self, appointment_id: uuid.UUID) -> Call | None: ...

    async def get_by_room_name(self, room_name: RoomName) -> Call | None: ...

    async def list(
        self,
        *,
        participant_id: str | None = None,
        doctor_id: str | None = None,
        patient_id: str | None = None,
        appointment_id: uuid.UUID | None = None,
        status: CallStatus | None = None,
        limit: int = 50,
        offset: int = 0,
    ) -> list[Call]: ...

    async def add(self, call: Call) -> None: ...

    async def update(self, call: Call) -> None: ...
