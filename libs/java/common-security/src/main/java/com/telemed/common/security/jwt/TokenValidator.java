package com.telemed.common.security.jwt;

import com.telemed.common.security.blacklist.TokenBlacklist;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;

@RequiredArgsConstructor
public final class TokenValidator {

  private final TokenBlacklist tokenBlacklist;
  private final boolean blacklistEnabled;

  public void validate(Jwt jwt) {
    if (!blacklistEnabled) return;

    String jti = jwt.getId();
    if (jti == null || jti.isBlank()) {
      throw buildException("JWT has no jti claim");
    }
    if (tokenBlacklist.isBlacklisted(jti)) {
      throw buildException("JWT is blacklisted (logout)");
    }
  }

  private JwtValidationException buildException(String message) {
    OAuth2Error error = new OAuth2Error("invalid_token", message, null);
    return new JwtValidationException(message, List.of(error));
  }
}
