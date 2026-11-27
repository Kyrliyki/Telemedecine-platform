package com.telemed.common.messaging.dlq;

/**
 * One dead-letter queue owned by a service.
 *
 * @param key stable operator-facing name, for example {@code auth} or {@code appointment}
 * @param queueName RabbitMQ DLQ name
 * @param exchange exchange the message should return to during replay
 * @param description short human-readable purpose
 */
public record DlqQueueDefinition(
    String key, String queueName, String exchange, String description) {
  public DlqQueueDefinition {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("key is required");
    }
    if (queueName == null || queueName.isBlank()) {
      throw new IllegalArgumentException("queueName is required");
    }
    if (exchange == null || exchange.isBlank()) {
      throw new IllegalArgumentException("exchange is required");
    }
    description = description == null ? "" : description;
  }
}
