"""Video-service events.

Producer: ``video-service``.
Exchange: ``video.events``.

Media never flows through RabbitMQ or the API Gateway. Events only describe
session lifecycle and LiveKit room metadata.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from .base import EventPayload


class VideoEventTypes:
    SESSION_CREATED = "video.session.created.v1"
    SESSION_ENDED = "video.session.ended.v1"


class VideoSessionCreatedPayload(EventPayload):
    session_id: UUID
    appointment_id: UUID | None = None
    room_name: str
    doctor_id: UUID
    patient_id: UUID
    created_at: datetime


class VideoSessionEndedPayload(EventPayload):
    session_id: UUID
    room_name: str
    ended_at: datetime
    duration_seconds: int
    patient_id: UUID | None = None
    doctor_id: UUID | None = None


VIDEO_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    VideoEventTypes.SESSION_CREATED: VideoSessionCreatedPayload,
    VideoEventTypes.SESSION_ENDED: VideoSessionEndedPayload,
}
