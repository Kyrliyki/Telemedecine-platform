"""Auth-service event types and payloads shared with Java consumers/producers."""

from __future__ import annotations

from datetime import datetime
from typing import Any
from uuid import UUID

from .base import EventPayload


class AuthEventTypes:
    USER_REGISTERED = "auth.user.registered.v1"
    USER_CREATED_BY_ADMIN = "auth.user.created_by_admin.v1"
    USER_INVITED = "auth.user.invited.v1"
    USER_ROLE_CHANGED = "auth.user.role_changed.v1"
    USER_BLOCKED = "auth.user.blocked.v1"
    USER_UNBLOCKED = "auth.user.unblocked.v1"
    USER_EMAIL_CHANGED = "auth.user.email_changed.v1"
    USER_EMAIL_VERIFIED = "auth.user.email_verified.v1"
    USER_EMAIL_VERIFICATION_REQUESTED = "auth.user.email_verification_requested.v1"
    USER_PASSWORD_CHANGED = "auth.user.password_changed.v1"
    OAUTH_LINK_CONFIRMATION_REQUESTED = "auth.user.oauth_link_confirmation_requested.v1"


class AuthPayload(EventPayload):
    """Backwards-compatible alias for the shared payload base class."""


class UserRegisteredPayload(AuthPayload):
    user_id: UUID
    email: str
    roles: list[str]
    email_verified: bool
    source: str


class UserCreatedByAdminPayload(AuthPayload):
    user_id: UUID
    email: str
    roles: list[str]
    created_by: UUID | None = None


class UserInvitedPayload(AuthPayload):
    user_id: UUID
    email: str
    roles: list[str]
    expires_at: datetime
    invited_by: UUID | None = None
    raw_token: str | None = None


class UserRoleChangedPayload(AuthPayload):
    user_id: UUID
    new_roles: list[str]
    old_roles: list[str]


class PasswordChangedPayload(AuthPayload):
    user_id: UUID


class UserBlockedPayload(AuthPayload):
    user_id: UUID
    reason: str | None = None


class UserUnblockedPayload(AuthPayload):
    user_id: UUID


class UserEmailVerifiedPayload(AuthPayload):
    user_id: UUID
    email: str


class UserEmailChangedPayload(AuthPayload):
    user_id: UUID
    old_email: str
    new_email: str


class EmailVerificationRequestedPayload(AuthPayload):
    user_id: UUID
    email: str
    expires_at: datetime
    raw_token: str


class OAuthLinkConfirmationRequestedPayload(AuthPayload):
    user_id: UUID
    email: str
    provider: str
    expires_at: datetime
    raw_token: str


AUTH_EVENT_PAYLOADS: dict[str, type[AuthPayload]] = {
    AuthEventTypes.USER_REGISTERED: UserRegisteredPayload,
    AuthEventTypes.USER_CREATED_BY_ADMIN: UserCreatedByAdminPayload,
    AuthEventTypes.USER_INVITED: UserInvitedPayload,
    AuthEventTypes.USER_ROLE_CHANGED: UserRoleChangedPayload,
    AuthEventTypes.USER_BLOCKED: UserBlockedPayload,
    AuthEventTypes.USER_UNBLOCKED: UserUnblockedPayload,
    AuthEventTypes.USER_EMAIL_CHANGED: UserEmailChangedPayload,
    AuthEventTypes.USER_EMAIL_VERIFIED: UserEmailVerifiedPayload,
    AuthEventTypes.USER_EMAIL_VERIFICATION_REQUESTED: EmailVerificationRequestedPayload,
    AuthEventTypes.USER_PASSWORD_CHANGED: PasswordChangedPayload,
    AuthEventTypes.OAUTH_LINK_CONFIRMATION_REQUESTED: OAuthLinkConfirmationRequestedPayload,
}


def parse_auth_payload(event_type: str, payload: dict[str, Any]) -> AuthPayload:
    model = AUTH_EVENT_PAYLOADS.get(event_type)
    if model is None:
        raise ValueError(f"Unknown auth event type: {event_type}")
    return model.model_validate(payload)
