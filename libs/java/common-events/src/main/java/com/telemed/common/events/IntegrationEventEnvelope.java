package com.telemed.common.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record IntegrationEventEnvelope(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        String correlationId,
        String payloadJson,
        int version
) {
    public IntegrationEventEnvelope {
        Objects.requireNonNull(eventId, "eventId required");
        Objects.requireNonNull(eventType, "eventType required");
        Objects.requireNonNull(occurredAt, "occurredAt required");
        Objects.requireNonNull(payloadJson, "payloadJson required");

    }
}