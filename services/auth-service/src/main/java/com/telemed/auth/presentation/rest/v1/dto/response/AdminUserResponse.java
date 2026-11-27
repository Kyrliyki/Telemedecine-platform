package com.telemed.auth.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
    UUID userId,
    String email,
    boolean emailVerified,
    String status,
    Set<String> roles,
    Instant createdAt,
    Instant updatedAt) {}
