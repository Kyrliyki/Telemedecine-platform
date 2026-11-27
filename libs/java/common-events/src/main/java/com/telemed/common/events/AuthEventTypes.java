package com.telemed.common.events;

/**
 * Shared routing keys and event type names for auth-service integration events.
 * Consumers should reference these constants instead of duplicating strings.
 */
public final class AuthEventTypes {

    private AuthEventTypes() {
    }

    public static final String USER_REGISTERED = "auth.user.registered.v1";
    public static final String USER_CREATED_BY_ADMIN = "auth.user.created_by_admin.v1";
    public static final String USER_INVITED = "auth.user.invited.v1";
    public static final String USER_ROLE_CHANGED = "auth.user.role_changed.v1";
    public static final String USER_BLOCKED = "auth.user.blocked.v1";
    public static final String USER_UNBLOCKED = "auth.user.unblocked.v1";
    public static final String USER_EMAIL_CHANGED = "auth.user.email_changed.v1";
    public static final String USER_EMAIL_VERIFIED = "auth.user.email_verified.v1";
    public static final String USER_EMAIL_VERIFICATION_REQUESTED =
            "auth.user.email_verification_requested.v1";
    public static final String USER_PASSWORD_CHANGED = "auth.user.password_changed.v1";
    public static final String OAUTH_LINK_CONFIRMATION_REQUESTED =
            "auth.user.oauth_link_confirmation_requested.v1";
}
