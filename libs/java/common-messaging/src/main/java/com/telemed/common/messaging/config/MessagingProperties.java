package com.telemed.common.messaging.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "messaging")
public record MessagingProperties(
    String exchange, String dlx, boolean publisherConfirms, Duration confirmTimeout, Queue queue) {
  public MessagingProperties {
    if (exchange == null || exchange.isBlank()) {
      throw new IllegalArgumentException("messaging.exchange required");
    }
    if (dlx == null || dlx.isBlank()) {
      dlx = exchange + ".dlx";
    }
    if (confirmTimeout == null || confirmTimeout.isZero() || confirmTimeout.isNegative()) {
      confirmTimeout = Duration.ofSeconds(5);
    }
    if (queue == null) {
      queue = new Queue(false, null, "#", null);
    }
  }

  /**
   * Optional consumer queue declaration. Disabled by default; consumers opt in with {@code
   * messaging.queue.enabled=true}.
   */
  public record Queue(
      boolean enabled, String name, String routingKey, String deadLetterRoutingKey) {
    public Queue {
      if (routingKey == null || routingKey.isBlank()) {
        routingKey = "#";
      }
    }
  }
}
