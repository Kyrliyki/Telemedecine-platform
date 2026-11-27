package com.telemed.auth.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth.bootstrap-admin")
public record BootstrapAdminProperties(boolean enabled, String email, String password) {}
