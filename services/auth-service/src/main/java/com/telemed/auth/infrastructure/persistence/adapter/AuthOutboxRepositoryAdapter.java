package com.telemed.auth.infrastructure.persistence.adapter;

import com.telemed.auth.infrastructure.persistence.entity.AuthOutboxEvent;
import com.telemed.auth.infrastructure.persistence.repository.JpaAuthOutboxRepository;
import com.telemed.common.outbox.entity.OutboxEvent;
import com.telemed.common.outbox.entity.OutboxStatus;
import com.telemed.common.outbox.port.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthOutboxRepositoryAdapter implements OutboxRepository {

  private final JpaAuthOutboxRepository jpa;
  private final String serviceName;

  public AuthOutboxRepositoryAdapter(
      JpaAuthOutboxRepository jpa, @Value("${spring.application.name}") String serviceName) {
    this.jpa = jpa;
    this.serviceName = serviceName;
  }

  @Override
  public void save(OutboxEvent event) {
    if (event.getService() == null) event.setService(serviceName);
    jpa.save((AuthOutboxEvent) event);
  }

  @Override
  public List<OutboxEvent> claimPending(
      String service, int batchSize, int maxAttempts, Instant now, Instant processingTimeout) {
    return jpa
        .claimPending(
            service,
            List.of(OutboxStatus.PENDING.name(), OutboxStatus.FAILED.name()),
            maxAttempts,
            now,
            processingTimeout,
            batchSize)
        .stream()
        .map(e -> (OutboxEvent) e)
        .toList();
  }

  @Override
  public void markSent(UUID eventId) {
    jpa.markSent(eventId, Instant.now(), OutboxStatus.SENT, OutboxStatus.PROCESSING);
  }

  @Override
  public void markFailed(UUID eventId, String reason, Instant nextAttemptAt, boolean dead) {
    jpa.markFailed(
        eventId,
        reason,
        nextAttemptAt,
        dead ? OutboxStatus.DEAD : OutboxStatus.FAILED,
        OutboxStatus.PROCESSING);
  }

  @Override
  public int markDeadStuck(String service, Instant cutoff, int maxAttempts) {
    return jpa.markDeadStuck(service, cutoff, maxAttempts);
  }

  @Override
  public int deleteSentBefore(String service, Instant cutoff) {
    return jpa.deleteBefore(service, OutboxStatus.SENT, cutoff);
  }

  @Override
  public int deleteDeadBefore(String service, Instant cutoff) {
    return jpa.deleteBefore(service, OutboxStatus.DEAD, cutoff);
  }
}
