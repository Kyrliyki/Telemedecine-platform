package com.telemed.user.infrastructure.messaging;

import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.ProcessedEventRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(ProcessedEventCleanupJob.class);

  private final ProcessedEventRepository repository;
  private final Clock clock;
  private final Duration retention;

  public ProcessedEventCleanupJob(
      ProcessedEventRepository repository,
      Clock clock,
      @Value("${user.processed-events.retention:30d}") Duration retention) {
    this.repository = repository;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "${user.processed-events.cleanup-cron:0 0 3 * * *}")
  public void cleanup() {
    int deleted = repository.deleteProcessedBefore(clock.now().minus(retention));
    if (deleted > 0) {
      log.info("Deleted {} processed_event rows older than {}", deleted, retention);
    }
  }
}
