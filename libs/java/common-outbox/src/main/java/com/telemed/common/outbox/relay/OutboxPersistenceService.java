package com.telemed.common.outbox.relay;

import com.telemed.common.outbox.entity.OutboxEvent;
import com.telemed.common.outbox.port.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class OutboxPersistenceService {

  private final OutboxRepository outboxRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<OutboxEvent> claimPending(
      String service, int batchSize, int maxAttempts, Instant now, Instant cutoff) {
    return outboxRepository.claimPending(service, batchSize, maxAttempts, now, cutoff);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int markDeadStuck(String service, Instant cutoff, int maxAttempts) {
    return outboxRepository.markDeadStuck(service, cutoff, maxAttempts);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markSent(UUID eventId) {
    outboxRepository.markSent(eventId);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markFailed(UUID eventId, String reason, Instant nextAttemptAt, boolean dead) {
    outboxRepository.markFailed(eventId, reason, nextAttemptAt, dead);
  }
}
