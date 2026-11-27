package com.telemed.schedule.infrastructure.health;

import org.springframework.amqp.rabbit.connection.Connection;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("rabbitConnectionHealthIndicator")
public class RabbitConnectionHealthIndicator implements HealthIndicator {

  private final ConnectionFactory connectionFactory;
  private final RabbitListenerEndpointRegistry listenerRegistry;

  public RabbitConnectionHealthIndicator(
      ConnectionFactory connectionFactory, RabbitListenerEndpointRegistry listenerRegistry) {
    this.connectionFactory = connectionFactory;
    this.listenerRegistry = listenerRegistry;
  }

  @Override
  public Health health() {
    boolean listenersRunning =
        !listenerRegistry.getListenerContainers().isEmpty()
            && listenerRegistry.getListenerContainers().stream()
                .allMatch(container -> container.isRunning());
    if (!listenersRunning) {
      return Health.down().withDetail("listeners", "not running").build();
    }
    Connection connection = null;
    try {
      connection = connectionFactory.createConnection();
      return Health.up().withDetail("broker", "reachable").build();
    } catch (Exception exception) {
      return Health.down(exception).build();
    } finally {
      if (connection != null) {
        connection.close();
      }
    }
  }
}
