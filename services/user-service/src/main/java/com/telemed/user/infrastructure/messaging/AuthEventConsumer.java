package com.telemed.user.infrastructure.messaging;

import com.telemed.common.events.AuthEventPayloads.UserBlockedPayload;
import com.telemed.common.events.AuthEventPayloads.UserCreatedByAdminPayload;
import com.telemed.common.events.AuthEventPayloads.UserEmailChangedPayload;
import com.telemed.common.events.AuthEventPayloads.UserInvitedPayload;
import com.telemed.common.events.AuthEventPayloads.UserRegisteredPayload;
import com.telemed.common.events.AuthEventPayloads.UserRoleChangedPayload;
import com.telemed.common.events.AuthEventPayloads.UserUnblockedPayload;
import com.telemed.common.events.AuthEventTypes;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.ProcessedEventRepository;
import com.telemed.user.application.service.UserSyncService;
import com.telemed.user.infrastructure.config.RabbitConfig;
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
  private static final String MDC_CORRELATION_ID = "correlationId";

  private final JsonMapper jsonMapper;
  private final UserSyncService userSyncService;
  private final ProcessedEventRepository processedEventRepository;
  private final AuthEventMetrics metrics;
  private final Clock clock;

  public AuthEventConsumer(
      JsonMapper jsonMapper,
      UserSyncService userSyncService,
      ProcessedEventRepository processedEventRepository,
      AuthEventMetrics metrics,
      Clock clock) {
    this.jsonMapper = jsonMapper;
    this.userSyncService = userSyncService;
    this.processedEventRepository = processedEventRepository;
    this.metrics = metrics;
    this.clock = clock;
  }

  @RabbitListener(queues = RabbitConfig.AUTH_QUEUE)
  @Transactional
  public void handle(Message message) {
    String eventType = header(message, MessageHeaders.EVENT_TYPE);
    UUID eventId = eventId(message);
    String correlationId = header(message, MessageHeaders.CORRELATION_ID);

    if (eventType == null || eventType.isBlank()) {
      metrics.recordFailure("unknown");
      log.warn(
          "Auth event without eventType header; messageId={}",
          message.getMessageProperties().getMessageId());
      return;
    }

    putCorrelationId(correlationId);
    try {
      if (eventId != null && !processedEventRepository.tryClaim(eventId, eventType, clock.now())) {
        metrics.recordDuplicate(eventType);
        log.info("Skipping duplicate auth event eventId={} type={}", eventId, eventType);
        return;
      }

      try {
        dispatch(eventType, message);
        metrics.recordSuccess(eventType);
      } catch (Exception exception) {
        metrics.recordFailure(eventType);
        log.error("Failed to process auth event eventId={} type={}", eventId, eventType, exception);
        throw exception;
      }
    } finally {
      clearCorrelationId();
    }
  }

  private void dispatch(String eventType, Message message) {
    switch (eventType) {
      case AuthEventTypes.USER_REGISTERED ->
          userSyncService.onUserRegistered(read(message, UserRegisteredPayload.class));
      case AuthEventTypes.USER_CREATED_BY_ADMIN ->
          userSyncService.onUserCreatedByAdmin(read(message, UserCreatedByAdminPayload.class));
      case AuthEventTypes.USER_INVITED ->
          userSyncService.onUserInvited(read(message, UserInvitedPayload.class));
      case AuthEventTypes.USER_ROLE_CHANGED ->
          userSyncService.onRoleChanged(read(message, UserRoleChangedPayload.class));
      case AuthEventTypes.USER_EMAIL_CHANGED ->
          userSyncService.onEmailChanged(read(message, UserEmailChangedPayload.class));
      case AuthEventTypes.USER_BLOCKED ->
          userSyncService.onBlocked(read(message, UserBlockedPayload.class));
      case AuthEventTypes.USER_UNBLOCKED ->
          userSyncService.onUnblocked(read(message, UserUnblockedPayload.class));
      default -> log.debug("Ignoring auth event type={}", eventType);
    }
  }

  private <T> T read(Message message, Class<T> type) {
    return jsonMapper.readValue(message.getBody(), type);
  }

  private static void putCorrelationId(String correlationId) {
    if (correlationId != null && !correlationId.isBlank()) {
      MDC.put(MDC_CORRELATION_ID, correlationId);
    }
  }

  private static void clearCorrelationId() {
    MDC.remove(MDC_CORRELATION_ID);
  }

  private static String header(Message message, String name) {
    Object value = message.getMessageProperties().getHeader(name);
    return value == null ? null : value.toString();
  }

  private static UUID eventId(Message message) {
    String value = header(message, MessageHeaders.EVENT_ID);
    if (value == null || value.isBlank()) {
      value = message.getMessageProperties().getMessageId();
    }
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ex) {
      return null;
    }
  }
}
