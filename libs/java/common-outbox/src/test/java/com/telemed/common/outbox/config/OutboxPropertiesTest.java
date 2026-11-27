package com.telemed.common.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxPropertiesTest {

  @Test
  void appliesDefaultsForMissingValues() {
    OutboxProperties properties = new OutboxProperties(0, null, 0, null, null, null);

    assertThat(properties.batchSize()).isEqualTo(100);
    assertThat(properties.retention()).isEqualTo(Duration.ofDays(7));
    assertThat(properties.maxAttempts()).isEqualTo(5);
    assertThat(properties.processingTimeout()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void backoffGrowsAndIsCapped() {
    OutboxProperties properties =
        new OutboxProperties(
            10,
            Duration.ofDays(1),
            5,
            Duration.ofSeconds(1),
            Duration.ofSeconds(8),
            Duration.ofMinutes(5));

    assertThat(properties.backoff(0)).isEqualTo(Duration.ofSeconds(1));
    assertThat(properties.backoff(2)).isEqualTo(Duration.ofSeconds(4));
    assertThat(properties.backoff(10)).isEqualTo(Duration.ofSeconds(8));
  }
}
