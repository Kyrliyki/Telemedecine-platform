package com.telemed.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.jwt")
public record JwtKeyProperties(String keyDirectory, String keyId) {
  public JwtKeyProperties {
    if (keyDirectory == null || keyDirectory.isBlank()) {
      keyDirectory = ".jwt";
    }
    if (keyId == null || keyId.isBlank()) {
      keyId = "auth-service-key";
    }
  }
}
