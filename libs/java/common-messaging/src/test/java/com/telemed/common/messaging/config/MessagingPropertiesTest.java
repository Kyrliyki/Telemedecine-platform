package com.telemed.common.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class MessagingPropertiesTest {

  @Test
  void appliesDefaults() {
    MessagingProperties properties = new MessagingProperties("auth.events", null, true, null, null);

    assertThat(properties.exchange()).isEqualTo("auth.events");
    assertThat(properties.dlx()).isEqualTo("auth.events.dlx");
    assertThat(properties.confirmTimeout()).isEqualTo(Duration.ofSeconds(5));
    assertThat(properties.queue().enabled()).isFalse();
    assertThat(properties.queue().routingKey()).isEqualTo("#");
    assertThat(properties.queue().name()).isNull();
  }

  @Test
  void requiresExchange() {
    assertThatThrownBy(() -> new MessagingProperties(" ", null, true, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("messaging.exchange");
  }

  @Test
  void queueRoutingKeyDefaultsToHash() {
    MessagingProperties properties =
        new MessagingProperties(
            "auth.events",
            null,
            true,
            null,
            new MessagingProperties.Queue(true, "auth.events.queue", " ", null));

    assertThat(properties.queue().routingKey()).isEqualTo("#");
    assertThat(properties.queue().enabled()).isTrue();
  }
}
