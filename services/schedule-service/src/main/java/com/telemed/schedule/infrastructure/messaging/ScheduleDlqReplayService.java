package com.telemed.schedule.infrastructure.messaging;

import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.schedule.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ScheduleDlqReplayService {

  private static final Logger log = LoggerFactory.getLogger(ScheduleDlqReplayService.class);

  private final RabbitTemplate rabbitTemplate;

  public ScheduleDlqReplayService(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public ReplayResult replay(String queueType, int limit) {
    String queue;
    String exchange;
    if ("appointment".equalsIgnoreCase(queueType)) {
      queue = RabbitConfig.APPOINTMENT_DLQ;
      exchange = RabbitConfig.APPOINTMENT_EXCHANGE;
    } else {
      queue = RabbitConfig.AUTH_DLQ;
      exchange = RabbitConfig.AUTH_EXCHANGE;
    }

    int replayed = 0;
    int failed = 0;
    for (int i = 0; i < limit; i++) {
      Message message = rabbitTemplate.receive(queue);
      if (message == null) {
        break;
      }
      String eventType = header(message, MessageHeaders.EVENT_TYPE);
      if (eventType == null || eventType.isBlank()) {
        failed++;
        requeue(queue, message);
        break;
      }
      try {
        clearDeadLetterHeaders(message);
        rabbitTemplate.send(exchange, eventType, message);
        replayed++;
      } catch (Exception exception) {
        failed++;
        requeue(queue, message);
        log.error("Failed to replay DLQ message type={}", eventType, exception);
        break;
      }
    }
    log.info("Schedule DLQ replay: queue={} replayed={} failed={}", queue, replayed, failed);
    return new ReplayResult(replayed, failed);
  }

  private void requeue(String queue, Message message) {
    try {
      rabbitTemplate.send("", queue, message);
    } catch (Exception exception) {
      log.error("Failed to return message to DLQ {}", queue, exception);
    }
  }

  private static void clearDeadLetterHeaders(Message message) {
    MessageProperties properties = message.getMessageProperties();
    properties.getHeaders().remove("x-death");
    properties.getHeaders().remove("x-first-death-reason");
    properties.getHeaders().remove("x-first-death-queue");
    properties.getHeaders().remove("x-first-death-exchange");
  }

  private static String header(Message message, String name) {
    Object value = message.getMessageProperties().getHeader(name);
    return value == null ? null : value.toString();
  }

  public record ReplayResult(int replayed, int failed) {}
}
