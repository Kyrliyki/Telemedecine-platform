package com.telemed.auth.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record AuthAuditView(
    UUID id,
    UUID actorId,
    UUID targetUserId,
    String action,
    String outcome,
    String ipAddress,
    String userAgent,
    String requestId,
    String correlationId,
    String details,
    Instant createdAt) {}
