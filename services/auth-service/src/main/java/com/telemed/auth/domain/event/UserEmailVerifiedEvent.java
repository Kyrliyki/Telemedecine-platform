package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;

public record UserEmailVerifiedEvent(UUID eventId, Instant occurredAt, UserId userId, Email email)
    implements DomainEvent {}
