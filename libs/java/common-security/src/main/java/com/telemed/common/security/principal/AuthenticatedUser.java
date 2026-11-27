package com.telemed.common.security.principal;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
    UUID userId, String email, Set<String> roles, String jti, Instant expiresAt) {
  public AuthenticatedUser {
    Objects.requireNonNull(userId, "userId required");
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  public boolean isAdmin() {
    return hasRole("ADMIN");
  }
}
