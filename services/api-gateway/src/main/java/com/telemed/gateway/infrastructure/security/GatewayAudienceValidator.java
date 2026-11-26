package com.telemed.gateway.infrastructure.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class GatewayAudienceValidator implements OAuth2TokenValidator<Jwt> {

  private final List<String> expectedAudience;

  public GatewayAudienceValidator(List<String> expectedAudience) {
    if (expectedAudience == null || expectedAudience.isEmpty()) {
      throw new IllegalArgumentException("expectedAudience must not be empty");
    }
    this.expectedAudience = List.copyOf(expectedAudience);
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt jwt) {
    List<String> tokenAudience = jwt.getAudience();
    if (tokenAudience != null && tokenAudience.stream().anyMatch(expectedAudience::contains)) {
      return OAuth2TokenValidatorResult.success();
    }
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(
            "invalid_token", "Token audience does not match: " + expectedAudience, null));
  }
}
