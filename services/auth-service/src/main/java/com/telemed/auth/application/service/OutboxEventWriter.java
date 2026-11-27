package com.telemed.auth.application.service;

import com.telemed.auth.application.mapper.DomainEventMapper;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.common.security.util.CorrelationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

  private final OutboxRepository outboxRepository;
  private final OutboxEventFactory outboxEventFactory;
  private final DomainEventMapper eventMapper;

  public void write(AuthUser user, String rawToken) {
    user.pullEvents()
        .forEach(
            event ->
                outboxRepository.save(
                    outboxEventFactory.toEntity(
                        eventMapper.map(event, CorrelationContext.current(), rawToken))));
  }
}
