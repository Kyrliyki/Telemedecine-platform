package com.telemed.gateway.domain.model;

import java.time.Instant;
import java.util.Set;

public record GatewayUser(
    String userId, String email, Set<String> roles, String jti, Instant expiresAt) {
  public GatewayUser {
    roles = roles == null ? Set.of() : Set.copyOf(roles);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }
}
