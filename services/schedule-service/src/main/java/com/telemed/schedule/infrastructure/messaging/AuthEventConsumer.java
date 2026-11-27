package com.telemed.schedule.infrastructure.messaging;

import com.telemed.common.events.AuthEventPayloads.UserRoleChangedPayload;
import com.telemed.common.events.AuthEventTypes;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.service.ScheduleCleanupService;
import com.telemed.schedule.infrastructure.config.RabbitConfig;
import com.telemed.schedule.infrastructure.persistence.adapter.ProcessedEventRepositoryAdapter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuthEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(AuthEventConsumer.class);

  private final JsonMapper jsonMapper;
  private final ScheduleCleanupService cleanupService;
  private final ProcessedEventRepositoryAdapter processedEvents;
  private final Clock clock;

  public AuthEventConsumer(
      JsonMapper jsonMapper,
      ScheduleCleanupService cleanupService,
      ProcessedEventRepositoryAdapter processedEvents,
      Clock clock) {
    this.jsonMapper = jsonMapper;
    this.cleanupService = cleanupService;
    this.processedEvents = processedEvents;
    this.clock = clock;
  }

  @RabbitListener(queues = RabbitConfig.AUTH_QUEUE)
  @Transactional
  public void handle(Message message) {
    String eventType = header(message, MessageHeaders.EVENT_TYPE);
    UUID eventId = eventId(message);
    String correlationId = header(message, MessageHeaders.CORRELATION_ID);
    if (eventType == null || eventType.isBlank()) {
      log.warn("Auth event without eventType header");
      return;
    }
    if (eventId != null && !processedEvents.tryClaim(eventId, eventType, clock.now())) {
      log.info("Skipping duplicate auth event eventId={} type={}", eventId, eventType);
      return;
    }
    if (correlationId != null && !correlationId.isBlank()) {
      MDC.put("correlationId", correlationId);
    }
    try {
      switch (eventType) {
        case AuthEventTypes.USER_ROLE_CHANGED -> {
          UserRoleChangedPayload payload = read(message, UserRoleChangedPayload.class);
          if (payload.newRoles().stream().noneMatch("DOCTOR"::equalsIgnoreCase)) {
            cleanupService.deleteDoctorSchedule(payload.userId());
          }
        }
        default -> log.debug("Ignoring auth event type={}", eventType);
      }
    } finally {
      MDC.remove("correlationId");
    }
  }

  private <T> T read(Message message, Class<T> type) {
    return jsonMapper.readValue(message.getBody(), type);
  }

  private static String header(Message message, String name) {
    Object value = message.getMessageProperties().getHeader(name);
    return value == null ? null : value.toString();
  }

  private static UUID eventId(Message message) {
    String value = header(message, "eventId");
    if (value == null || value.isBlank()) {
      value = message.getMessageProperties().getMessageId();
    }
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }
}
