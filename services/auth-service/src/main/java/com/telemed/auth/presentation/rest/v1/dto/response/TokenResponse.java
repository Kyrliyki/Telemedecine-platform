package com.telemed.auth.presentation.rest.v1.dto.response;

import java.time.Instant;

public record TokenResponse(
    String accessToken,
    String refreshToken,
    Instant accessExpiresAt,
    Instant refreshExpiresAt,
    boolean mustChangePassword) {}
