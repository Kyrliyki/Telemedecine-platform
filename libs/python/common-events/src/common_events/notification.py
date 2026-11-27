"""Notification-service events.

Producer: ``notification-service``.
Exchange: ``notification.events``.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from .base import EventPayload


class NotificationEventTypes:
    SENT = "notification.notification.sent.v1"


class NotificationSentPayload(EventPayload):
    notification_id: UUID
    user_id: str
    kind: str
    channel: str
    sent_at: datetime


NOTIFICATION_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    NotificationEventTypes.SENT: NotificationSentPayload,
}
