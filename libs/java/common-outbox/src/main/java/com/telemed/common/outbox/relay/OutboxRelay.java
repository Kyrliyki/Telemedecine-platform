package com.telemed.common.outbox.relay;

import com.telemed.common.events.EventPublisher;
import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.outbox.config.OutboxProperties;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.metrics.OutboxMetrics;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
  private static final int MAX_ERROR_LENGTH = 1000;

  private final OutboxPersistenceService persistence;
  private final EventPublisher eventPublisher;
  private final OutboxProperties properties;
  private final OutboxMetrics metrics;
  private final OutboxEventFactory eventFactory;
  private final String serviceName;

  @Scheduled(fixedDelayString = "${outbox.relay.interval:1000}")
  public void relay() {
    var now = Instant.now();
    var cutoff = now.minus(properties.processingTimeout());

    int deadStuck = persistence.markDeadStuck(serviceName, cutoff, properties.maxAttempts());
    if (deadStuck > 0) {
      metrics.recordDead(deadStuck);
      log.warn(
          "Outbox recovery: service={}, marked {} stuck events as DEAD", serviceName, deadStuck);
    }

    var claimed =
        persistence.claimPending(
            serviceName, properties.batchSize(), properties.maxAttempts(), now, cutoff);
    if (claimed.isEmpty()) {
      metrics.recordPending(0);
      return;
    }
    metrics.recordPending(claimed.size());

    int sent = 0, failed = 0, dead = 0;

    for (var event : claimed) {
      try {
        IntegrationEventEnvelope envelope = eventFactory.toEnvelope(event);
        eventPublisher.publish(envelope);
        persistence.markSent(event.getEventId());
        sent++;
      } catch (Exception ex) {
        int newRetryCount = event.getRetryCount() + 1;
        boolean isDead = newRetryCount >= properties.maxAttempts();
        Instant nextAttemptAt = isDead ? null : now.plus(properties.backoff(newRetryCount));

        persistence.markFailed(
            event.getEventId(), truncate(ex.getMessage()), nextAttemptAt, isDead);
        if (isDead) {
          dead++;
          log.error(
              "Event {} (type={}, service={}) is DEAD after {} attempts: {}",
              event.getEventId(),
              event.getEventType(),
              event.getService(),
              newRetryCount,
              ex.getMessage());
        } else {
          failed++;
          log.warn(
              "Failed to publish event {} (attempt {}/{}), next attempt at {}: {}",
              event.getEventId(),
              newRetryCount,
              properties.maxAttempts(),
              nextAttemptAt,
              ex.getMessage());
        }
      }
    }
    metrics.recordSent(sent);
    metrics.recordFailed(failed);
    metrics.recordDead(dead);
    log.debug(
        "Outbox relay: service={}, sent={}, failed={}, dead={}, pending={}",
        serviceName,
        sent,
        failed,
        dead,
        claimed.size());
  }

  private String truncate(String s) {
    if (s == null) return null;
    return s.length() > MAX_ERROR_LENGTH ? s.substring(0, MAX_ERROR_LENGTH) : s;
  }
}
