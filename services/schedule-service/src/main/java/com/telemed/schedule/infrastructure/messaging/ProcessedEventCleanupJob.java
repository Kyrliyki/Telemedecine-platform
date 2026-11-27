package com.telemed.schedule.infrastructure.messaging;

import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.infrastructure.persistence.repository.JpaProcessedEventRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanupJob.class);

  private final JpaProcessedEventRepository repository;
  private final Clock clock;
  private final Duration retention;

  public ProcessedEventCleanupJob(
      JpaProcessedEventRepository repository,
      Clock clock,
      @Value("${schedule.processed-events.retention:30d}") Duration retention) {
    this.repository = repository;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "${schedule.processed-events.cleanup-cron:0 0 3 * * *}")
  public void cleanup() {
    long deleted = repository.deleteByProcessedAtBefore(clock.now().minus(retention));
    if (deleted > 0) {
      log.info("Deleted {} processed_event rows older than {}", deleted, retention);
    }
  }
}
