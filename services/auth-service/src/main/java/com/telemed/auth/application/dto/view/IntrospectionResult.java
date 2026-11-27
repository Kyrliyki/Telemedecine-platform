package com.telemed.auth.application.dto.view;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record IntrospectionResult(
    boolean active, UUID userId, Set<String> roles, Instant expiresAt, String reason) {}
