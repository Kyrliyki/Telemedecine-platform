package com.telemed.emr.application.service;

import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.common.security.util.CorrelationContext;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EmrEventWriter {

  private final OutboxRepository outboxRepository;
  private final OutboxEventFactory eventFactory;
  private final JsonMapper jsonMapper;

  public EmrEventWriter(
      OutboxRepository outboxRepository, OutboxEventFactory eventFactory, JsonMapper jsonMapper) {
    this.outboxRepository = outboxRepository;
    this.eventFactory = eventFactory;
    this.jsonMapper = jsonMapper;
  }

  public void write(String eventType, Object payload, Instant occurredAt) {
    try {
      String json = jsonMapper.writeValueAsString(payload);
      IntegrationEventEnvelope envelope =
          new IntegrationEventEnvelope(
              UUID.randomUUID(), eventType, occurredAt, CorrelationContext.current(), json, 1);
      outboxRepository.save(eventFactory.toEntity(envelope));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to create " + eventType + " event", exception);
    }
  }
}
