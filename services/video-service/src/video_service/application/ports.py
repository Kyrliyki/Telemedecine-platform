"""Порты прикладного слоя: внешние сервисы, транзакции, время, события."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import StrEnum
from types import TracebackType
from typing import Protocol
from uuid import UUID

from video_service.application.dto import Actor, CallView
from video_service.domain.events import DomainEvent
from video_service.domain.ports import CallRepository
from video_service.domain.value_objects import CallId, RoomName


class LiveKitGateway(Protocol):
    """Порт внешнего сервиса LiveKit (SFU)."""

    async def create_room(
        self,
        *,
        room_name: RoomName,
        metadata: dict[str, str],
        max_participants: int,
    ) -> None: ...

    async def delete_room(self, *, room_name: RoomName) -> None: ...

    async def create_participant_token(
        self,
        *,
        room_name: RoomName,
        identity: str,
        display_name: str,
        ttl: timedelta,
        metadata: dict[str, str],
    ) -> str: ...

    async def healthcheck(self) -> bool: ...


class UnitOfWork(Protocol):
    """Транзакционная граница. Реализации: PostgreSQL, in-memory."""

    @property
    def calls(self) -> CallRepository: ...

    async def __aenter__(self) -> UnitOfWork: ...

    async def __aexit__(
        self,
        exc_type: type[BaseException] | None,
        exc: BaseException | None,
        tb: TracebackType | None,
    ) -> None: ...

    async def commit(self) -> None: ...


class UnitOfWorkFactory(Protocol):
    def __call__(self) -> UnitOfWork: ...


class Clock(Protocol):
    def now(self) -> datetime: ...


class IdGenerator(Protocol):
    def new_call_id(self) -> CallId: ...


@dataclass(frozen=True, slots=True)
class AppointmentInfo:
    appointment_id: UUID
    patient_id: UUID
    doctor_id: UUID
    status: str


class AppointmentDirectory(Protocol):
    async def get(self, appointment_id: UUID) -> AppointmentInfo | None: ...


class EventPublisher(Protocol):
    """Публикация интеграционных событий (RabbitMQ / outbox)."""

    async def publish(self, event: DomainEvent) -> None: ...


class CallNotifier(Protocol):
    """Realtime-уведомление пациента о начале звонка (best-effort)."""

    async def notify_started(self, call: CallView) -> None: ...


class RoomEventKind(StrEnum):
    ROOM_STARTED = "room_started"
    ROOM_FINISHED = "room_finished"


@dataclass(frozen=True, slots=True)
class LiveKitRoomEvent:
    kind: RoomEventKind
    room_name: RoomName
    event_id: str


class WebhookParser(Protocol):
    """Проверяет подпись вебхука LiveKit и превращает его в доменно-нейтральное событие."""

    def parse(self, body: bytes, auth_header: str) -> LiveKitRoomEvent | None: ...


class LiveKitGatewayError(Exception):
    """Ошибка взаимодействия с LiveKit (недоступен, отклонил запрос и т.п.)."""


class WebhookVerificationError(Exception):
    """Вебхук не прошёл проверку подписи."""


class InvalidTokenError(Exception):
    """JWT отсутствует, истёк или подписан не тем ключом."""


class TokenVerifier(Protocol):
    """Порт проверки пользовательского JWT (реализуется infrastructure.auth)."""

    def verify(self, token: str) -> Actor: ...
