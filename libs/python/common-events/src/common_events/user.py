"""User-service events shared with Python consumers.

Producer: ``user-service`` (Java).
Exchange: ``user.events``.

Only identifiers and non-clinical profile data are carried here. Notification
consumers use these events to inform doctors about verification decisions and
profile changes.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import Field

from .base import EventPayload


class UserEventTypes:
    PROFILE_UPDATED = "user.profile.updated.v1"
    DOCTOR_VERIFIED = "user.doctor.verified.v1"
    DOCTOR_REJECTED = "user.doctor.rejected.v1"


class UserProfileUpdatedPayload(EventPayload):
    user_id: UUID
    email: str | None = None
    first_name: str | None = None
    last_name: str | None = None
    display_name: str | None = None
    roles: list[str] = Field(default_factory=list)
    status: str | None = None
    updated_at: datetime | None = None


class DoctorVerifiedPayload(EventPayload):
    user_id: UUID
    email: str | None = None
    specialization: str | None = None
    license_number: str | None = None
    verified_at: datetime | None = None
    verified_by: UUID | None = None


class DoctorRejectedPayload(EventPayload):
    user_id: UUID
    email: str | None = None
    reason: str | None = None
    rejected_at: datetime | None = None
    rejected_by: UUID | None = None


USER_EVENT_PAYLOADS: dict[str, type[EventPayload]] = {
    UserEventTypes.PROFILE_UPDATED: UserProfileUpdatedPayload,
    UserEventTypes.DOCTOR_VERIFIED: DoctorVerifiedPayload,
    UserEventTypes.DOCTOR_REJECTED: DoctorRejectedPayload,
}
