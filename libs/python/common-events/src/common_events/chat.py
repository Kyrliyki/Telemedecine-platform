"""Chat-service events.

Producer: ``chat-service``.
Exchange: ``chat.events``.

Messages are stored in PostgreSQL (schema ``chat``). These events carry only
metadata and identifiers; never put message text or attachments into an
integration event.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from .base import EventPayload


class ChatEventTypes:
    ROOM_CREATED = "chat.room.created.v1"
    ROOM_CLOSED = "chat.room.closed.v1"
    MESSAGE_SENT = "chat.message.sent.v1"


class ChatRoomCreatedPayload(EventPayload):
    room_id: UUID
    appointment_id: UUID | None = None
    participant_ids: list[UUID]
    created_at: datetime


class ChatRoomClosedPayload(EventPayload):
    room_id: UUID
    closed_at: datetime
    closed_by: UUID | None = None


class ChatMessageSentPayload(EventPayload):
    room_id: UUID
    message_id: UUID
    sender_id: UUID
    sent_at: datetime


CHAT_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    ChatEventTypes.ROOM_CREATED: ChatRoomCreatedPayload,
    ChatEventTypes.ROOM_CLOSED: ChatRoomClosedPayload,
    ChatEventTypes.MESSAGE_SENT: ChatMessageSentPayload,
}
