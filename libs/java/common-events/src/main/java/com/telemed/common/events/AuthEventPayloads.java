package com.telemed.common.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared payload contracts for auth-service integration events.
 *
 * <p>Keeping payloads in the common module prevents consumers from drifting
 * away from the producer schema.
 */
public final class AuthEventPayloads {

    private AuthEventPayloads() {
    }

    public record UserRegisteredPayload(
            UUID userId,
            String email,
            List<String> roles,
            boolean emailVerified,
            String source
    ) {
        public UserRegisteredPayload {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record UserCreatedByAdminPayload(
            UUID userId,
            String email,
            List<String> roles,
            UUID createdBy
    ) {
        public UserCreatedByAdminPayload {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record UserInvitedPayload(
            UUID userId,
            String email,
            List<String> roles,
            Instant expiresAt,
            UUID invitedBy,
            String rawToken
    ) {
        public UserInvitedPayload {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record UserRoleChangedPayload(
            UUID userId,
            List<String> newRoles,
            List<String> oldRoles
    ) {
        public UserRoleChangedPayload {
            newRoles = newRoles == null ? List.of() : List.copyOf(newRoles);
            oldRoles = oldRoles == null ? List.of() : List.copyOf(oldRoles);
        }
    }

    public record PasswordChangedPayload(UUID userId) {
    }

    public record UserBlockedPayload(UUID userId, String reason) {
    }

    public record UserUnblockedPayload(UUID userId) {
    }

    public record UserEmailVerifiedPayload(UUID userId, String email) {
    }

    public record UserEmailChangedPayload(UUID userId, String oldEmail, String newEmail) {
    }

    public record EmailVerificationRequestedPayload(
            UUID userId,
            String email,
            Instant expiresAt,
            String rawToken
    ) {
    }

    public record OAuthLinkConfirmationRequestedPayload(
            UUID userId,
            String email,
            String provider,
            Instant expiresAt,
            String rawToken
    ) {
    }
}
