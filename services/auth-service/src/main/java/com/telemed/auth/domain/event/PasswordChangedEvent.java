package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;

public record PasswordChangedEvent(UUID eventId, Instant occurredAt, UserId userId)
    implements DomainEvent {}
