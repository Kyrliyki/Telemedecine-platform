"""HTTP schemas for NotificationService."""

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal
from uuid import UUID

from pydantic import BaseModel, Field

from notification_service.domain.entities import Notification


class NotificationResponse(BaseModel):
    id: UUID
    kind: str
    title: str
    body: str
    payload: dict[str, Any]
    created_at: datetime
    sent_at: datetime | None = None
    read_at: datetime | None = None

    @classmethod
    def from_entity(cls, notification: Notification) -> NotificationResponse:
        return cls(
            id=notification.id,
            kind=notification.kind,
            title=notification.title,
            body=notification.body,
            payload=notification.payload,
            created_at=notification.created_at,
            sent_at=notification.sent_at,
            read_at=notification.read_at,
        )


class NotificationPage(BaseModel):
    items: list[NotificationResponse]
    total: int
    limit: int
    offset: int


class UnreadCountResponse(BaseModel):
    count: int


class ReadAllResponse(BaseModel):
    updated: int


class DevNotificationRequest(BaseModel):
    title: str = Field(min_length=1, max_length=255)
    body: str = Field(min_length=1, max_length=4000)
    kind: str = "system.test"
    channel: Literal["in_app", "email"] = "in_app"
    user_id: str | None = None
    email: str | None = None


class DevContactRequest(BaseModel):
    user_id: str = Field(min_length=1, max_length=128)
    email: str = Field(min_length=3, max_length=320)


class DevDispatchResponse(BaseModel):
    processed: int
