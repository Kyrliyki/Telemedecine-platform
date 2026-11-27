package com.telemed.emr.infrastructure.messaging;

import com.telemed.emr.application.port.output.Clock;
import com.telemed.emr.infrastructure.persistence.adapter.ProcessedEventRepositoryAdapter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanupJob.class);

  private final ProcessedEventRepositoryAdapter repository;
  private final Clock clock;
  private final Duration retention;

  public ProcessedEventCleanupJob(
      ProcessedEventRepositoryAdapter repository,
      Clock clock,
      @Value("${emr.processed-events.retention:30d}") Duration retention) {
    this.repository = repository;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "${emr.processed-events.cleanup-cron:0 0 3 * * *}")
  public void cleanup() {
    int deleted = repository.deleteBefore(clock.now().minus(retention));
    if (deleted > 0) {
      log.info("Deleted {} processed_event rows older than {}", deleted, retention);
    }
  }
}
