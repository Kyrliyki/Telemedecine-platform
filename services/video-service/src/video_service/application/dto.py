"""DTO прикладного слоя: команды, актор и результаты use cases."""

from __future__ import annotations

import uuid
from dataclasses import dataclass
from datetime import datetime

from video_service.domain.entities import Call
from video_service.domain.enums import ActorRole, CallStatus
from video_service.domain.errors import ForbiddenError
from video_service.domain.value_objects import CallId


@dataclass(frozen=True, slots=True)
class Actor:
    """Аутентифицированный пользователь (из JWT AuthService)."""

    identity: str
    roles: frozenset[ActorRole]
    display_name: str | None = None

    @property
    def is_admin(self) -> bool:
        return ActorRole.ADMIN in self.roles

    def require_any_role(self, *roles: ActorRole) -> None:
        if self.is_admin or self.roles.intersection(roles):
            return
        allowed = ", ".join(role.value for role in roles)
        raise ForbiddenError(f"Требуется одна из ролей: {allowed}")


@dataclass(frozen=True, slots=True)
class CreateCallCommand:
    appointment_id: uuid.UUID
    patient_id: str
    doctor_id: str | None = None
    max_participants: int = 2


@dataclass(frozen=True, slots=True)
class JoinCallCommand:
    call_id: CallId
    display_name: str | None = None


@dataclass(frozen=True, slots=True)
class GetCallQuery:
    call_id: CallId


@dataclass(frozen=True, slots=True)
class ListCallsQuery:
    appointment_id: uuid.UUID | None = None
    status: CallStatus | None = None
    doctor_id: str | None = None
    patient_id: str | None = None
    limit: int = 50
    offset: int = 0


@dataclass(frozen=True, slots=True)
class CallView:
    """Read-модель звонка, отдаваемая наружу."""

    id: str
    appointment_id: str
    room_name: str
    doctor_id: str
    patient_id: str
    status: CallStatus
    max_participants: int
    created_at: datetime
    started_at: datetime | None
    ended_at: datetime | None

    @classmethod
    def from_entity(cls, call: Call) -> CallView:
        return cls(
            id=str(call.id),
            appointment_id=str(call.appointment_id),
            room_name=call.room_name.value,
            doctor_id=call.doctor_id,
            patient_id=call.patient_id,
            status=call.status,
            max_participants=call.max_participants,
            created_at=call.created_at,
            started_at=call.started_at,
            ended_at=call.ended_at,
        )


@dataclass(frozen=True, slots=True)
class CreateCallResult:
    call: CallView
    created: bool


@dataclass(frozen=True, slots=True)
class JoinCallResult:
    call: CallView
    token: str
    livekit_url: str
    expires_at: datetime


@dataclass(frozen=True, slots=True)
class CallPage:
    items: list[CallView]
    limit: int
    offset: int
