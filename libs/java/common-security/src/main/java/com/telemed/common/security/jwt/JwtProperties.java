package com.telemed.common.security.jwt;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(
    String issuer,
    String jwkSetUri,
    List<String> audience,
    Duration clockSkew,
    boolean blacklistEnabled,
    Duration jwkSetTimeout) {
  public JwtProperties {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("security.jwt.issuer is required");
    }
    if (jwkSetUri == null || jwkSetUri.isBlank()) {
      throw new IllegalArgumentException("security.jwt.jwk-set-uri is required");
    }
    if (audience == null || audience.isEmpty()) {
      throw new IllegalArgumentException("security.jwt.audience is required");
    }
    if (clockSkew == null) {
      clockSkew = Duration.ofSeconds(30);
    }
    if (jwkSetTimeout == null || jwkSetTimeout.isNegative() || jwkSetTimeout.isZero()) {
      jwkSetTimeout = Duration.ofSeconds(5);
    }
  }
}
