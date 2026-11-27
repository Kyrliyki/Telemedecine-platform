package com.telemed.auth.infrastructure.persistence.adapter;

import com.telemed.auth.application.port.output.OutboxAppender;
import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JpaOutboxAppender implements OutboxAppender {
  private final OutboxRepository repository;
  private final OutboxEventFactory factory;

  public void append(IntegrationEventEnvelope envelope) {
    repository.save(factory.toEntity(envelope));
  }
}
