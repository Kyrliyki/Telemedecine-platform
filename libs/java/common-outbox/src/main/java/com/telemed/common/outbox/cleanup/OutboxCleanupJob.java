package com.telemed.common.outbox.cleanup;

import com.telemed.common.outbox.config.OutboxProperties;
import com.telemed.common.outbox.port.OutboxRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public class OutboxCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(OutboxCleanupJob.class);

  private final OutboxRepository outboxRepository;
  private final OutboxProperties properties;
  private final String serviceName;

  @Scheduled(cron = "${outbox.cleanup.cron:0 0 3 * * *}")
  @Transactional
  public void cleanup() {
    Instant cutoff = Instant.now().minus(properties.retention());
    int deletedSent = outboxRepository.deleteSentBefore(serviceName, cutoff);
    int deletedDead = outboxRepository.deleteDeadBefore(serviceName, cutoff);
    if (deletedSent > 0 || deletedDead > 0) {
      log.info(
          "Outbox cleanup: service={}, deleted SENT={}, DEAD={} older than {}",
          serviceName,
          deletedSent,
          deletedDead,
          cutoff);
    }
  }
}
