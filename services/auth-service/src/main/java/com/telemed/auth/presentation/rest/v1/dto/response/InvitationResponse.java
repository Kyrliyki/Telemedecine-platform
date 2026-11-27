package com.telemed.auth.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(UUID userId, String email, Instant expiresAt) {}
