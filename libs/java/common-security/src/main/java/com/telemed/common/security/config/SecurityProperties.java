package com.telemed.common.security.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security")
public record SecurityProperties(Internal internal) {
  public SecurityProperties {
    if (internal == null) {
      internal = new Internal(false, null, "", null);
    }
  }

  public record Internal(boolean enabled, List<String> allowedCidrs, String token, String header) {
    private static final List<String> PRIVATE_NETWORKS =
        List.of("127.0.0.1/32", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16");

    public Internal {
      if (allowedCidrs == null) {
        allowedCidrs = PRIVATE_NETWORKS;
      } else {
        allowedCidrs = List.copyOf(allowedCidrs);
      }
      if (token == null) {
        token = "";
      }
      if (header == null || header.isBlank()) {
        header = "X-Internal-Token";
      }
    }
  }
}
