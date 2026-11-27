package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.UUID;

public record UserBlockedEvent(
    UUID eventId, Instant occurredAt, UserId userId, String reason, UserId blockedBy)
    implements DomainEvent {}
