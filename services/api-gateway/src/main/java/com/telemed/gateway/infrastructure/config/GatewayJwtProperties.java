package com.telemed.gateway.infrastructure.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.jwt")
public record GatewayJwtProperties(
    String issuer,
    String jwkSetUri,
    List<String> audience,
    Duration clockSkew,
    boolean blacklistEnabled) {
  public GatewayJwtProperties {
    if (issuer == null || issuer.isBlank()) {
      throw new IllegalArgumentException("security.jwt.issuer is required");
    }
    if (jwkSetUri == null || jwkSetUri.isBlank()) {
      throw new IllegalArgumentException("security.jwt.jwk-set-uri is required");
    }
    if (audience == null || audience.isEmpty()) {
      throw new IllegalArgumentException("security.jwt.audience is required");
    }
    audience = List.copyOf(audience);
    if (clockSkew == null) {
      clockSkew = Duration.ofSeconds(30);
    }
  }
}
