package com.telemed.common.outbox.port;

import com.telemed.common.outbox.entity.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository {
  void save(OutboxEvent event);

  List<OutboxEvent> claimPending(
      String service, int batchSize, int maxAttempts, Instant now, Instant cutoff);

  int markDeadStuck(String service, Instant cutoff, int maxAttempts);

  void markSent(UUID eventId);

  void markFailed(UUID eventId, String reason, Instant nextAttemptAt, boolean dead);

  int deleteSentBefore(String service, Instant cutoff);

  int deleteDeadBefore(String service, Instant cutoff);
}
