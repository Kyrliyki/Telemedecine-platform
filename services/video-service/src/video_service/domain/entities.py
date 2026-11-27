"""Сущность звонка и её бизнес-правила."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime

from video_service.domain.enums import ActorRole, CallStatus
from video_service.domain.errors import (
    CallEndedError,
    ForbiddenError,
    ValidationError,
)
from video_service.domain.value_objects import CallId, ParticipantIdentity, RoomName

MIN_PARTICIPANTS = 2
MAX_PARTICIPANTS = 100


@dataclass(slots=True)
class Call:
    """Звонок — это комната LiveKit, привязанная к приёму (appointment)."""

    id: CallId
    appointment_id: uuid.UUID
    room_name: RoomName
    doctor_id: str
    patient_id: str
    status: CallStatus
    max_participants: int
    created_at: datetime
    started_at: datetime | None = None
    ended_at: datetime | None = None

    @classmethod
    def create(
        cls,
        *,
        call_id: CallId,
        appointment_id: uuid.UUID,
        doctor_id: str,
        patient_id: str,
        max_participants: int,
        now: datetime,
    ) -> Call:
        doctor = ParticipantIdentity(doctor_id)
        patient = ParticipantIdentity(patient_id)

        if doctor == patient:
            raise ValidationError("Врач и пациент не могут быть одним участником")

        if not MIN_PARTICIPANTS <= max_participants <= MAX_PARTICIPANTS:
            raise ValidationError(
                f"max_participants должен быть в диапазоне {MIN_PARTICIPANTS}..{MAX_PARTICIPANTS}",
            )

        return cls(
            id=call_id,
            appointment_id=appointment_id,
            room_name=RoomName.for_call(call_id),
            doctor_id=doctor.value,
            patient_id=patient.value,
            status=CallStatus.CREATED,
            max_participants=max_participants,
            created_at=now,
        )

    @property
    def is_ended(self) -> bool:
        return self.status is CallStatus.ENDED

    def is_participant(self, identity: str) -> bool:
        return identity in {self.doctor_id, self.patient_id}

    def ensure_can_join(self, identity: str, roles: frozenset[ActorRole]) -> None:
        """Присоединиться могут только участники приёма или администратор."""
        if self.is_ended:
            raise CallEndedError("Звонок уже завершён")

        if ActorRole.ADMIN in roles or self.is_participant(identity):
            return

        raise ForbiddenError("Пользователь не является участником звонка")

    def ensure_can_manage(self, identity: str, roles: frozenset[ActorRole]) -> None:
        """Управлять звонком (завершать) может врач-владелец или администратор."""
        if ActorRole.ADMIN in roles or identity == self.doctor_id:
            return
        raise ForbiddenError("Управлять звонком может только врач или администратор")

    def mark_started(self, now: datetime) -> bool:
        """Переводит звонок в статус active. Возвращает True, если статус изменился."""
        if self.status is not CallStatus.CREATED:
            return False
        self.status = CallStatus.ACTIVE
        self.started_at = now
        return True

    def end(self, now: datetime) -> bool:
        """Завершает звонок. Идемпотентно: повторное завершение статус не меняет."""
        if self.is_ended:
            return False
        self.status = CallStatus.ENDED
        self.ended_at = now
        return True
