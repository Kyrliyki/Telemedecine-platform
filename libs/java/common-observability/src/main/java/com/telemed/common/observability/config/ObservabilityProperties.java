package com.telemed.common.observability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "observability")
public record ObservabilityProperties(
        String service,
        String environment
) {
    public ObservabilityProperties {
        if (service == null || service.isBlank()) {
            service = "unknown-service";
        }
        if (environment == null || environment.isBlank()) {
            environment = "unknown-env";
        }
    }
}
