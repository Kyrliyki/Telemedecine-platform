package com.telemed.common.messaging.health;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

@RequiredArgsConstructor
public class RabbitHealthIndicator implements HealthIndicator {

  private final RabbitTemplate rabbitTemplate;

  @Override
  public Health health() {
    try {
      rabbitTemplate.execute(
          channel -> {
            channel.exchangeDeclarePassive("amq.direct");
            return null;
          });
      return Health.up().build();
    } catch (Exception ex) {
      return Health.down(ex).build();
    }
  }
}
