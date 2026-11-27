package com.telemed.gateway.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatewayUserTest {

  @Test
  void copiesRolesAndChecksRole() {
    Set<String> roles = new HashSet<>(Set.of("DOCTOR", "ADMIN"));
    GatewayUser user =
        new GatewayUser(
            "user-1", "user@example.com", roles, "jti-1", Instant.parse("2026-01-01T11:00:00Z"));

    roles.add("PATIENT");

    assertThat(user.roles()).containsExactlyInAnyOrder("DOCTOR", "ADMIN");
    assertThat(user.hasRole("ADMIN")).isTrue();
    assertThat(user.hasRole("PATIENT")).isFalse();
  }
}
