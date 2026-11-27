package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserCreatedByAdminEvent(
    UUID eventId,
    Instant occurredAt,
    UserId userId,
    Email email,
    Set<UserRole> roles,
    UserId createdBy)
    implements DomainEvent {}
