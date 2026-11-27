"""In-memory реализация хранилища звонков (для локального запуска и тестов)."""

from __future__ import annotations

import asyncio
import copy
import uuid
from dataclasses import dataclass, field

from video_service.domain.entities import Call
from video_service.domain.enums import CallStatus
from video_service.domain.errors import CallAlreadyExistsError, CallNotFoundError
from video_service.domain.ports import CallRepository
from video_service.domain.value_objects import CallId, RoomName


@dataclass
class InMemoryCallStore:
    calls: dict[CallId, Call] = field(default_factory=dict)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    def reset(self) -> None:
        self.calls.clear()


class InMemoryCallRepository(CallRepository):
    def __init__(self, store: InMemoryCallStore) -> None:
        self._store = store

    async def get(self, call_id: CallId) -> Call | None:
        async with self._store.lock:
            call = self._store.calls.get(call_id)
            return copy.deepcopy(call) if call else None

    async def get_current_by_appointment(self, appointment_id: uuid.UUID) -> Call | None:
        async with self._store.lock:
            calls = [
                call
                for call in self._store.calls.values()
                if call.appointment_id == appointment_id and call.status is not CallStatus.ENDED
            ]
            if not calls:
                return None
            current = max(calls, key=lambda call: call.created_at)
            return copy.deepcopy(current)

    async def get_by_room_name(self, room_name: RoomName) -> Call | None:
        async with self._store.lock:
            for call in self._store.calls.values():
                if call.room_name == room_name:
                    return copy.deepcopy(call)
            return None

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
    ) -> list[Call]:
        async with self._store.lock:
            items = list(self._store.calls.values())

            if participant_id is not None:
                items = [
                    call for call in items if participant_id in {call.doctor_id, call.patient_id}
                ]
            if doctor_id is not None:
                items = [call for call in items if call.doctor_id == doctor_id]
            if patient_id is not None:
                items = [call for call in items if call.patient_id == patient_id]
            if appointment_id is not None:
                items = [call for call in items if call.appointment_id == appointment_id]
            if status is not None:
                items = [call for call in items if call.status is status]

            items.sort(key=lambda call: call.created_at, reverse=True)
            return [copy.deepcopy(call) for call in items[offset : offset + limit]]

    async def add(self, call: Call) -> None:
        async with self._store.lock:
            if call.id in self._store.calls:
                raise CallAlreadyExistsError(f"Звонок {call.id} уже существует")
            if any(
                existing.appointment_id == call.appointment_id
                and existing.status is not CallStatus.ENDED
                for existing in self._store.calls.values()
            ):
                raise CallAlreadyExistsError(
                    f"Активный звонок для приёма {call.appointment_id} уже существует",
                )
            self._store.calls[call.id] = copy.deepcopy(call)

    async def update(self, call: Call) -> None:
        async with self._store.lock:
            if call.id not in self._store.calls:
                raise CallNotFoundError(f"Звонок {call.id} не найден")
            self._store.calls[call.id] = copy.deepcopy(call)


class InMemoryUnitOfWork:
    """Транзакции эмулируются: commit — no-op, данные уже в общем store."""

    def __init__(self, store: InMemoryCallStore) -> None:
        self._store = store
        self.calls: CallRepository = InMemoryCallRepository(store)

    async def __aenter__(self) -> InMemoryUnitOfWork:
        return self

    async def __aexit__(self, exc_type: object, exc: object, tb: object) -> None:
        return None

    async def commit(self) -> None:
        return None


def in_memory_uow_factory(store: InMemoryCallStore) -> InMemoryUnitOfWork:
    return InMemoryUnitOfWork(store)
