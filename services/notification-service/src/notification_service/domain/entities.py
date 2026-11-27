"""Notification entity and contact projection."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any
from uuid import UUID

from notification_service.domain.enums import NotificationChannel, NotificationStatus


@dataclass(slots=True)
class Notification:
    id: UUID
    user_id: str
    kind: str
    channel: NotificationChannel
    title: str
    body: str
    payload: dict[str, Any]
    status: NotificationStatus
    attempts: int
    dedup_key: str
    recipient_email: str | None
    correlation_id: str | None
    error: str | None
    next_attempt_at: datetime | None
    processing_started_at: datetime | None
    created_at: datetime
    sent_at: datetime | None
    read_at: datetime | None


@dataclass(frozen=True, slots=True)
class NotificationDraft:
    """A notification that should be persisted for later delivery."""

    user_id: str
    kind: str
    channel: NotificationChannel
    title: str
    body: str
    payload: dict[str, Any] = field(default_factory=dict)
    recipient_email: str | None = None
    dedup_key: str = ""
    correlation_id: str | None = None


@dataclass(frozen=True, slots=True)
class Contact:
    user_id: str
    email: str
