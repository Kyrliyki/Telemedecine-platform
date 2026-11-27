from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from uuid import UUID

from .value_objects import ParticipantRole, RequestStatus


@dataclass(slots=True)
class Participant:
    user_id: UUID
    role: ParticipantRole


@dataclass(slots=True)
class Message:
    id: UUID
    dialog_id: UUID
    author_id: UUID
    content: str
    client_msg_id: UUID
    created_at: datetime
    edited_at: datetime | None = None
    deleted_at: datetime | None = None


@dataclass(slots=True)
class Dialog:
    id: UUID
    request_id: str
    request_status: RequestStatus
    participants: list[Participant] = field(default_factory=list)
    created_at: datetime | None = None
    updated_at: datetime | None = None

    def is_participant(self, user_id: UUID) -> bool:
        return any(p.user_id == user_id for p in self.participants)

    def is_open(self) -> bool:
        return self.request_status == RequestStatus.OPEN


@dataclass(slots=True)
class DialogUserState:
    dialog_id: UUID
    user_id: UUID
    last_read_message_id: UUID | None
    unread_count: int
    archived: bool