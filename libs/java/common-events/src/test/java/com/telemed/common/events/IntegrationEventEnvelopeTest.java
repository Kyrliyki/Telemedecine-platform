package com.telemed.common.events;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationEventEnvelopeTest {

    @Test
    void createsEnvelope() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        var envelope = new IntegrationEventEnvelope(
                id, "auth.user.registered.v1", now, "correlation-1", "{}", 1);

        assertThat(envelope.eventId()).isEqualTo(id);
        assertThat(envelope.eventType()).isEqualTo("auth.user.registered.v1");
        assertThat(envelope.occurredAt()).isEqualTo(now);
        assertThat(envelope.correlationId()).isEqualTo("correlation-1");
        assertThat(envelope.payloadJson()).isEqualTo("{}");
        assertThat(envelope.version()).isEqualTo(1);
    }

    @Test
    void rejectsMissingRequiredFields() {
        Instant now = Instant.now();
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new IntegrationEventEnvelope(
                null, "type", now, null, "{}", 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrationEventEnvelope(
                id, null, now, null, "{}", 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrationEventEnvelope(
                id, "type", null, null, "{}", 1))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IntegrationEventEnvelope(
                id, "type", now, null, null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
