package com.telemed.common.messaging.dlq;

import com.telemed.common.messaging.header.MessageHeaders;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Lists, replays and purges dead-letter queues owned by one service.
 *
 * <p>Replay preserves the original event headers, removes RabbitMQ dead-letter metadata and
 * publishes the message back to the source exchange. Consumers remain idempotent, so a duplicate
 * replay is safe.
 */
public class DlqAdminService {

  private static final Logger log = LoggerFactory.getLogger(DlqAdminService.class);
  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

  private final RabbitTemplate rabbitTemplate;
  private final DlqQueueCatalog catalog;

  public DlqAdminService(RabbitTemplate rabbitTemplate, DlqQueueCatalog catalog) {
    this.rabbitTemplate = rabbitTemplate;
    this.catalog = catalog;
  }

  public List<DlqQueueView> list() {
    return catalog.queues().stream()
        .map(
            definition -> {
              long count = messageCount(definition.queueName());
              return new DlqQueueView(
                  definition.key(),
                  definition.queueName(),
                  definition.exchange(),
                  definition.description(),
                  count,
                  count >= 0);
            })
        .toList();
  }

  public DlqReplayResult replay(String key, int limit, boolean dryRun) {
    DlqQueueDefinition definition = require(key);
    int safeLimit = Math.max(1, Math.min(limit, 1000));
    long candidates = messageCount(definition.queueName());
    if (candidates == 0) {
      return new DlqReplayResult(definition.key(), 0, 0, dryRun, 0);
    }
    if (dryRun) {
      return new DlqReplayResult(definition.key(), 0, 0, true, candidates);
    }

    int replayed = 0;
    int failed = 0;
    for (int i = 0; i < safeLimit; i++) {
      Message message = rabbitTemplate.receive(definition.queueName());
      if (message == null) {
        break;
      }
      String routingKey = resolveRoutingKey(message);
      if (routingKey == null || routingKey.isBlank()) {
        failed++;
        requeue(definition.queueName(), message);
        log.warn(
            "DLQ message without eventType/routing key cannot be replayed queue={}",
            definition.queueName());
        break;
      }
      try {
        clearDeadLetterHeaders(message);
        publish(definition.exchange(), routingKey, message);
        replayed++;
      } catch (Exception exception) {
        failed++;
        requeue(definition.queueName(), message);
        log.error(
            "Failed to replay DLQ message queue={} routingKey={}",
            definition.queueName(),
            routingKey,
            exception);
        break;
      }
    }
    log.info(
        "DLQ replay finished: queue={} replayed={} failed={}",
        definition.queueName(),
        replayed,
        failed);
    return new DlqReplayResult(definition.key(), replayed, failed, false, candidates);
  }

  public DlqPurgeResult purge(String key) {
    DlqQueueDefinition definition = require(key);
    try {
      Integer purged =
          rabbitTemplate.execute(
              channel -> channel.queuePurge(definition.queueName()).getMessageCount());
      long count = purged == null ? 0 : purged;
      log.warn("DLQ purged: queue={} messages={}", definition.queueName(), count);
      return new DlqPurgeResult(definition.key(), count);
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Failed to purge DLQ " + definition.key(), exception);
    }
  }

  private void publish(String exchange, String routingKey, Message message) throws Exception {
    CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
    rabbitTemplate.send(exchange, routingKey, message, correlation);
    CorrelationData.Confirm confirm =
        correlation.getFuture().get(CONFIRM_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    if (!confirm.isAck()) {
      throw new IllegalStateException("RabbitMQ NACK during DLQ replay: " + confirm.getReason());
    }
    if (correlation.getReturned() != null) {
      throw new IllegalStateException(
          "RabbitMQ returned DLQ replay message: exchange="
              + correlation.getReturned().getExchange()
              + ", routingKey="
              + correlation.getReturned().getRoutingKey());
    }
  }

  private long messageCount(String queueName) {
    try {
      Long count = rabbitTemplate.execute(channel -> channel.messageCount(queueName));
      return count == null ? 0 : count;
    } catch (RuntimeException exception) {
      log.debug("DLQ is not available queue={}", queueName, exception);
      return -1;
    }
  }

  private DlqQueueDefinition require(String key) {
    return catalog.find(key).orElseThrow(() -> new IllegalArgumentException("Unknown DLQ: " + key));
  }

  private void requeue(String queueName, Message message) {
    try {
      rabbitTemplate.send("", queueName, message);
    } catch (RuntimeException exception) {
      log.error("Failed to return message to DLQ {}; message may be lost", queueName, exception);
    }
  }

  private static String resolveRoutingKey(Message message) {
    MessageProperties properties = message.getMessageProperties();
    String eventType = header(properties, MessageHeaders.EVENT_TYPE);
    if (eventType != null && !eventType.isBlank()) {
      return eventType;
    }
    Object xDeath = properties.getHeaders().get("x-death");
    if (xDeath instanceof List<?> entries) {
      for (Object entry : entries) {
        if (entry instanceof Map<?, ?> map) {
          Object routingKeys = map.get("routing-keys");
          if (routingKeys instanceof List<?> keys && !keys.isEmpty()) {
            Object first = keys.getFirst();
            if (first != null && !first.toString().isBlank()) {
              return first.toString();
            }
          }
        }
      }
    }
    return null;
  }

  private static void clearDeadLetterHeaders(Message message) {
    MessageProperties properties = message.getMessageProperties();
    properties.getHeaders().remove("x-death");
    properties.getHeaders().remove("x-first-death-reason");
    properties.getHeaders().remove("x-first-death-queue");
    properties.getHeaders().remove("x-first-death-exchange");
    properties.getHeaders().remove("x-last-death-reason");
    properties.getHeaders().remove("x-last-death-queue");
    properties.getHeaders().remove("x-last-death-exchange");
  }

  private static String header(MessageProperties properties, String name) {
    Object value = properties.getHeaders().get(name);
    return value == null ? null : value.toString();
  }
}
