"""Domain enums for notifications."""

from __future__ import annotations

from enum import StrEnum


class NotificationChannel(StrEnum):
    IN_APP = "in_app"
    EMAIL = "email"


class NotificationStatus(StrEnum):
    PENDING = "pending"
    PROCESSING = "processing"
    SENT = "sent"
    FAILED = "failed"
    DEAD = "dead"


class NotificationKind(StrEnum):
    AUTH_WELCOME = "auth.welcome"
    AUTH_EMAIL_VERIFICATION = "auth.email_verification"
    AUTH_OAUTH_LINK = "auth.oauth_link"
    AUTH_INVITED = "auth.invited"
    AUTH_BLOCKED = "auth.blocked"
    AUTH_UNBLOCKED = "auth.unblocked"
    AUTH_ROLE_CHANGED = "auth.role_changed"
    AUTH_PASSWORD_CHANGED = "auth.password_changed"
    AUTH_EMAIL_VERIFIED = "auth.email_verified"
    AUTH_EMAIL_CHANGED = "auth.email_changed"

    USER_PROFILE_UPDATED = "user.profile_updated"
    USER_DOCTOR_VERIFIED = "user.doctor_verified"
    USER_DOCTOR_REJECTED = "user.doctor_rejected"

    APPOINTMENT_CREATED = "appointment.created"
    APPOINTMENT_CONFIRMED = "appointment.confirmed"
    APPOINTMENT_RESCHEDULED = "appointment.rescheduled"
    APPOINTMENT_CANCELLED = "appointment.cancelled"
    APPOINTMENT_COMPLETED = "appointment.completed"
    APPOINTMENT_NO_SHOW = "appointment.no_show"

    VIDEO_CALL_STARTED = "video.call_started"
    VIDEO_CALL_ENDED = "video.call_ended"

    SYSTEM_TEST = "system.test"

    @property
    def is_token_link(self) -> bool:
        """Email kinds that contain a one-time token in the body."""
        return self in {NotificationKind.AUTH_EMAIL_VERIFICATION, NotificationKind.AUTH_OAUTH_LINK}
