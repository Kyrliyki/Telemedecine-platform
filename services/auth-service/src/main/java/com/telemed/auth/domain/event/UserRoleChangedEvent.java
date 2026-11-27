package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserRoleChangedEvent(
    UUID eventId,
    Instant occurredAt,
    UserId userId,
    Set<UserRole> newRoles,
    Set<UserRole> oldRoles,
    UserId changedBy)
    implements DomainEvent {

  public UserRoleChangedEvent {
    newRoles = Set.copyOf(newRoles);
    oldRoles = Set.copyOf(oldRoles);
  }
}
