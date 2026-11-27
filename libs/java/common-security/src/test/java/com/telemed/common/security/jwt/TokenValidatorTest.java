package com.telemed.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telemed.common.security.blacklist.TokenBlacklist;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

class TokenValidatorTest {

  private final TokenBlacklist blacklist = mock(TokenBlacklist.class);
  private final Jwt jwt =
      Jwt.withTokenValue("token")
          .header("alg", "RS256")
          .subject("user-1")
          .jti("jti-1")
          .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
          .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
          .build();

  @Test
  void acceptsTokenWhenBlacklistIsDisabled() {
    TokenValidator validator = new TokenValidator(blacklist, false);

    assertThatCode(() -> validator.validate(jwt)).doesNotThrowAnyException();
  }

  @Test
  void rejectsBlacklistedToken() {
    when(blacklist.isBlacklisted("jti-1")).thenReturn(true);
    TokenValidator validator = new TokenValidator(blacklist, true);

    assertThatThrownBy(() -> validator.validate(jwt))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("blacklisted");
  }

  @Test
  void rejectsTokenWithoutJtiWhenBlacklistIsEnabled() {
    Jwt withoutJti =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject("user-1")
            .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
            .build();
    TokenValidator validator = new TokenValidator(blacklist, true);

    assertThatThrownBy(() -> validator.validate(withoutJti))
        .isInstanceOf(JwtValidationException.class)
        .hasMessageContaining("jti");
  }
}
