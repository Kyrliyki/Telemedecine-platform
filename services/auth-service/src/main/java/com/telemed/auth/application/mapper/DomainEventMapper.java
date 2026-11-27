package com.telemed.auth.application.mapper;

import static com.telemed.common.events.AuthEventPayloads.EmailVerificationRequestedPayload;
import static com.telemed.common.events.AuthEventPayloads.OAuthLinkConfirmationRequestedPayload;
import static com.telemed.common.events.AuthEventPayloads.PasswordChangedPayload;
import static com.telemed.common.events.AuthEventPayloads.UserBlockedPayload;
import static com.telemed.common.events.AuthEventPayloads.UserCreatedByAdminPayload;
import static com.telemed.common.events.AuthEventPayloads.UserEmailChangedPayload;
import static com.telemed.common.events.AuthEventPayloads.UserEmailVerifiedPayload;
import static com.telemed.common.events.AuthEventPayloads.UserInvitedPayload;
import static com.telemed.common.events.AuthEventPayloads.UserRegisteredPayload;
import static com.telemed.common.events.AuthEventPayloads.UserRoleChangedPayload;
import static com.telemed.common.events.AuthEventPayloads.UserUnblockedPayload;

import com.telemed.auth.domain.event.*;
import com.telemed.common.events.AuthEventTypes;
import com.telemed.common.events.IntegrationEventEnvelope;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class DomainEventMapper {
  private final JsonMapper objectMapper;

  public DomainEventMapper(JsonMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public IntegrationEventEnvelope map(DomainEvent event, String correlationId, String rawToken) {
    return switch (event) {
      case UserRegisteredEvent e ->
          envelope(
              AuthEventTypes.USER_REGISTERED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserRegisteredPayload(
                  e.userId().value(),
                  e.email().value(),
                  e.roles().stream().map(Enum::name).toList(),
                  e.emailVerified(),
                  e.source().name()));

      case UserCreatedByAdminEvent e ->
          envelope(
              AuthEventTypes.USER_CREATED_BY_ADMIN,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserCreatedByAdminPayload(
                  e.userId().value(),
                  e.email().value(),
                  e.roles().stream().map(Enum::name).toList(),
                  e.createdBy() == null ? null : e.createdBy().value()));

      case UserEmailChangedEvent e ->
          envelope(
              AuthEventTypes.USER_EMAIL_CHANGED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserEmailChangedPayload(
                  e.userId().value(), e.oldEmail().value(), e.newEmail().value()));

      case PasswordChangedEvent e ->
          envelope(
              AuthEventTypes.USER_PASSWORD_CHANGED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new PasswordChangedPayload(e.userId().value()));

      case UserInvitedEvent e ->
          envelope(
              AuthEventTypes.USER_INVITED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserInvitedPayload(
                  e.userId().value(),
                  e.email().value(),
                  e.roles().stream().map(Enum::name).toList(),
                  e.invitationExpiresAt(),
                  e.invitedBy() == null ? null : e.invitedBy().value(),
                  rawToken));
      case UserRoleChangedEvent e ->
          envelope(
              AuthEventTypes.USER_ROLE_CHANGED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserRoleChangedPayload(
                  e.userId().value(),
                  e.newRoles().stream().map(Enum::name).toList(),
                  e.oldRoles().stream().map(Enum::name).toList()));
      case UserBlockedEvent e ->
          envelope(
              AuthEventTypes.USER_BLOCKED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserBlockedPayload(e.userId().value(), e.reason()));
      case UserUnblockedEvent e ->
          envelope(
              AuthEventTypes.USER_UNBLOCKED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserUnblockedPayload(e.userId().value()));
      case UserEmailVerifiedEvent e ->
          envelope(
              AuthEventTypes.USER_EMAIL_VERIFIED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new UserEmailVerifiedPayload(e.userId().value(), e.email().value()));
      case UserEmailVerificationRequestedEvent e ->
          envelope(
              AuthEventTypes.USER_EMAIL_VERIFICATION_REQUESTED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new EmailVerificationRequestedPayload(
                  e.userId().value(), e.email().value(), e.expiresAt(), rawToken));
      case OAuthLinkConfirmationRequestedEvent e ->
          envelope(
              AuthEventTypes.OAUTH_LINK_CONFIRMATION_REQUESTED,
              e.eventId(),
              e.occurredAt(),
              correlationId,
              new OAuthLinkConfirmationRequestedPayload(
                  e.userId().value(),
                  e.email().value(),
                  e.provider().name(),
                  e.expiresAt(),
                  rawToken));
      default ->
          throw new IllegalArgumentException(
              "Unsupported domain event: " + event.getClass().getName());
    };
  }

  private IntegrationEventEnvelope envelope(
      String type, UUID eventId, java.time.Instant at, String correlationId, Object payload) {
    try {
      return new IntegrationEventEnvelope(
          eventId, type, at, correlationId, objectMapper.writeValueAsString(payload), 1);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize " + type, ex);
    }
  }
}
