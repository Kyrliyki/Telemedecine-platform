package com.telemed.gateway.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.cors")
public record GatewayCorsProperties(List<String> allowedOrigins) {
  public GatewayCorsProperties {
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      allowedOrigins = List.of("http://localhost:3000");
    } else {
      allowedOrigins = List.copyOf(allowedOrigins);
    }
  }
}
