package com.telemed.user.infrastructure.messaging;

import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.user.infrastructure.audit.UserAuditLogger;
import com.telemed.user.infrastructure.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class DlqReplayService {

  private static final Logger log = LoggerFactory.getLogger(DlqReplayService.class);

  private final RabbitTemplate rabbitTemplate;
  private final UserAuditLogger audit;

  public DlqReplayService(RabbitTemplate rabbitTemplate, UserAuditLogger audit) {
    this.rabbitTemplate = rabbitTemplate;
    this.audit = audit;
  }

  public ReplayResult replay(int limit) {
    int replayed = 0;
    int failed = 0;
    for (int i = 0; i < limit; i++) {
      Message message = rabbitTemplate.receive(RabbitConfig.AUTH_DLQ);
      if (message == null) {
        break;
      }
      String eventType = header(message, MessageHeaders.EVENT_TYPE);
      if (eventType == null || eventType.isBlank()) {
        failed++;
        requeue(message);
        log.warn("DLQ message without eventType cannot be replayed");
        break;
      }
      try {
        clearDeadLetterHeaders(message);
        rabbitTemplate.send(RabbitConfig.AUTH_EXCHANGE, eventType, message);
        replayed++;
      } catch (Exception exception) {
        failed++;
        requeue(message);
        log.error("Failed to replay DLQ message type={}", eventType, exception);
        break;
      }
    }
    log.info("DLQ replay finished: replayed={}, failed={}", replayed, failed);
    audit.dlqReplay(replayed, failed);
    return new ReplayResult(replayed, failed);
  }

  private void requeue(Message message) {
    try {
      rabbitTemplate.send("", RabbitConfig.AUTH_DLQ, message);
    } catch (Exception exception) {
      log.error("Failed to return message to DLQ; message may be lost", exception);
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
