package com.telemed.gateway.infrastructure.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gateway.dlq")
public record GatewayDlqProperties(String internalToken, List<Service> services) {

  public GatewayDlqProperties {
    if (internalToken == null || internalToken.isBlank()) {
      throw new IllegalArgumentException("gateway.dlq.internal-token is required");
    }
    services = services == null ? List.of() : List.copyOf(services);
  }

  public record Service(String name, String baseUrl) {
    public Service {
      if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("gateway.dlq.services[].name is required");
      }
      if (baseUrl == null || baseUrl.isBlank()) {
        throw new IllegalArgumentException("gateway.dlq.services[].base-url is required");
      }
    }
  }
}
