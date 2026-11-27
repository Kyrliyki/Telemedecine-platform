package com.telemed.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class AudienceValidatorTest {

  @Test
  void acceptsMatchingAudience() {
    AudienceValidator validator = new AudienceValidator(List.of("telemedicine"));
    Jwt jwt = jwtWithAudience(List.of("other", "telemedicine"));

    assertThat(validator.validate(jwt).hasErrors()).isFalse();
  }

  @Test
  void rejectsMismatchedAudience() {
    AudienceValidator validator = new AudienceValidator(List.of("telemedicine"));
    Jwt jwt = jwtWithAudience(List.of("other"));

    assertThat(validator.validate(jwt).hasErrors()).isTrue();
  }

  private static Jwt jwtWithAudience(List<String> audience) {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject("user-1")
        .audience(audience)
        .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
        .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
        .build();
  }
}
