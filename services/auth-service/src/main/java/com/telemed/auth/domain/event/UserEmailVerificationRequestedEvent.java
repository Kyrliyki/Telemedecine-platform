package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;

public record UserEmailVerificationRequestedEvent(
    UUID eventId, Instant occurredAt, UserId userId, Email email, Instant expiresAt)
    implements DomainEvent {}
