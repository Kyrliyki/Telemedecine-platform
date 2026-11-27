package com.telemed.common.outbox.factory;

import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.outbox.entity.OutboxEvent;
import com.telemed.common.outbox.entity.OutboxStatus;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OutboxEventFactory {

  private final String serviceName;
  private final Supplier<? extends OutboxEvent> eventSupplier;

  public OutboxEvent toEntity(IntegrationEventEnvelope envelope) {
    Objects.requireNonNull(envelope, "envelope required");

    OutboxEvent event = eventSupplier.get();
    event.setId(UUID.randomUUID());
    event.setEventId(envelope.eventId());
    event.setEventType(envelope.eventType());
    event.setService(serviceName);
    event.setPayload(envelope.payloadJson());
    event.setCorrelationId(envelope.correlationId());
    event.setStatus(OutboxStatus.PENDING);
    event.setCreatedAt(envelope.occurredAt());
    event.setRetryCount(0);
    event.setNextAttemptAt(null);
    event.setProcessingStartedAt(null);
    return event;
  }

  public IntegrationEventEnvelope toEnvelope(OutboxEvent event) {
    Objects.requireNonNull(event, "event required");

    return new IntegrationEventEnvelope(
        event.getEventId(),
        event.getEventType(),
        event.getCreatedAt(),
        event.getCorrelationId(),
        event.getPayload(),
        1);
  }
}
