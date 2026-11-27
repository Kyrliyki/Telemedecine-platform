package com.telemed.emr.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record EmrAuditView(
    UUID id,
    UUID recordId,
    UUID actorId,
    String action,
    String ipAddress,
    String requestId,
    String correlationId,
    String details,
    Instant createdAt) {}
