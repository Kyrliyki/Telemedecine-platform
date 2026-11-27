from datetime import datetime
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, Field


class ParticipantIn(BaseModel):
    user_id: UUID
    role: Literal["client", "operator"]


class CreateDialogIn(BaseModel):
    request_id: str
    participants: list[ParticipantIn] = Field(min_length=1)
    request_status: Literal["open", "closed"] = "open"


class ParticipantOut(BaseModel):
    user_id: UUID
    role: str


class MessageOut(BaseModel):
    id: UUID
    dialog_id: UUID
    author_id: UUID
    content: str
    created_at: datetime
    edited_at: datetime | None = None
    deleted_at: datetime | None = None


class DialogOut(BaseModel):
    id: UUID
    request_id: str
    request_status: str
    participants: list[ParticipantOut]
    created_at: datetime | None
    updated_at: datetime | None


class DialogWithHistoryOut(DialogOut):
    messages: dict


class UpdateDialogIn(BaseModel):
    request_status: Literal["open", "closed"] | None = None
    archived: bool | None = None
    participants: list[ParticipantIn] | None = None


class MarkReadIn(BaseModel):
    up_to_message_id: UUID