package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;

public record UserUnblockedEvent(
    UUID eventId, Instant occurredAt, UserId userId, UserId unblockedBy) implements DomainEvent {}
