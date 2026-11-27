package com.telemed.user.infrastructure.client;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user.auth-service")
public record AuthServiceProperties(
    String baseUrl,
    String internalToken,
    int pageSize,
    boolean reconciliationEnabled,
    Duration reconcileInterval,
    Duration reconcileInitialDelay) {

  public AuthServiceProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "http://localhost:8080";
    }
    if (internalToken == null) {
      internalToken = "";
    }
    if (pageSize <= 0 || pageSize > 100) {
      pageSize = 100;
    }
    if (reconcileInterval == null || reconcileInterval.isNegative()) {
      reconcileInterval = Duration.ofHours(1);
    }
    if (reconcileInitialDelay == null || reconcileInitialDelay.isNegative()) {
      reconcileInitialDelay = Duration.ofSeconds(30);
    }
  }
}
