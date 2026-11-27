package com.telemed.schedule.application.service;

import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.TimeSlotRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SlotRetentionCleanupJob {

  private static final Logger log = LoggerFactory.getLogger(SlotRetentionCleanupJob.class);

  private final TimeSlotRepository repository;
  private final Clock clock;
  private final Duration retention;

  public SlotRetentionCleanupJob(
      TimeSlotRepository repository,
      Clock clock,
      @Value("${schedule.slot-retention:365d}") Duration retention) {
    this.repository = repository;
    this.clock = clock;
    this.retention = retention;
  }

  @Scheduled(cron = "${schedule.slot-retention-cron:0 30 3 * * *}")
  public void cleanup() {
    int deleted = repository.deleteEndedBefore(clock.now().minus(retention));
    if (deleted > 0) {
      log.info("Deleted {} historical time_slot rows older than {}", deleted, retention);
    }
  }
}
