package com.telemed.auth.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record InvitationResult(UUID userId, String email, Instant expiresAt) {}
