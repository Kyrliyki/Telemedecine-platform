package com.telemed.common.outbox.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox")
public record OutboxProperties(
    int batchSize,
    Duration retention,
    int maxAttempts,
    Duration initialBackoff,
    Duration maxBackoff,
    Duration processingTimeout) {
  public OutboxProperties {
    if (batchSize <= 0) batchSize = 100;
    if (retention == null) retention = Duration.ofDays(7);
    if (maxAttempts <= 0) maxAttempts = 5;
    if (initialBackoff == null) initialBackoff = Duration.ofSeconds(1);
    if (maxBackoff == null) maxBackoff = Duration.ofMinutes(5);
    if (processingTimeout == null) processingTimeout = Duration.ofMinutes(5);
  }

  public Duration backoff(int retryCount) {
    long multiplier = 1L << Math.min(retryCount, 20);
    Duration backoff = initialBackoff.multipliedBy(multiplier);
    return backoff.compareTo(maxBackoff) > 0 ? maxBackoff : backoff;
  }
}
