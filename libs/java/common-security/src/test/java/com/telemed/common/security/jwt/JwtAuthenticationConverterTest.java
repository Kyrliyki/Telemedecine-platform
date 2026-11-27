package com.telemed.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class JwtAuthenticationConverterTest {

  private final JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

  @Test
  void mapsRolesClaimToRoleAuthorities() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .claim("roles", List.of("DOCTOR", "ADMIN"))
            .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
            .build();

    var authentication = converter.convert(jwt);

    assertThat(authentication.getName()).isEqualTo("user-1");
    assertThat(authentication.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactlyInAnyOrder("ROLE_DOCTOR", "ROLE_ADMIN");
  }

  @Test
  void acceptsMissingRolesClaim() {
    Jwt jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
            .build();

    assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
  }
}
