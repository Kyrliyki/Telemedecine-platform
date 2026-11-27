package com.telemed.auth.domain.event;

import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserRegisteredEvent(
    UUID eventId,
    Instant occurredAt,
    UserId userId,
    Email email,
    Set<UserRole> roles,
    boolean emailVerified,
    RegistrationSource source)
    implements DomainEvent {
  public enum RegistrationSource {
    PASSWORD,
    GOOGLE
  }

  public UserRegisteredEvent {
    roles = Set.copyOf(roles);
  }
}
