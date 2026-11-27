package com.telemed.schedule.infrastructure.messaging;

import com.telemed.common.events.AppointmentEventPayloads.AppointmentCancelledPayload;
import com.telemed.common.events.AppointmentEventPayloads.AppointmentRescheduledPayload;
import com.telemed.common.events.AppointmentEventTypes;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.schedule.application.dto.command.ReleaseSlotCommand;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.service.SlotReservationService;
import com.telemed.schedule.infrastructure.config.RabbitConfig;
import com.telemed.schedule.infrastructure.persistence.adapter.ProcessedEventRepositoryAdapter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AppointmentEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(AppointmentEventConsumer.class);

  private final JsonMapper jsonMapper;
  private final SlotReservationService reservationService;
  private final ProcessedEventRepositoryAdapter processedEvents;
  private final Clock clock;

  public AppointmentEventConsumer(
      JsonMapper jsonMapper,
      SlotReservationService reservationService,
      ProcessedEventRepositoryAdapter processedEvents,
      Clock clock) {
    this.jsonMapper = jsonMapper;
    this.reservationService = reservationService;
    this.processedEvents = processedEvents;
    this.clock = clock;
  }

  @RabbitListener(queues = RabbitConfig.APPOINTMENT_QUEUE)
  @Transactional
  public void handle(Message message) {
    String eventType = header(message, MessageHeaders.EVENT_TYPE);
    UUID eventId = eventId(message);
    if (eventType == null || eventType.isBlank()) {
      log.warn("Appointment event without eventType header, rejecting to DLQ");
      throw new AmqpRejectAndDontRequeueException("Appointment event without eventType header");
    }
    if (eventId != null && !processedEvents.tryClaim(eventId, eventType, clock.now())) {
      log.info("Skipping duplicate appointment event eventId={} type={}", eventId, eventType);
      return;
    }
    String correlationId = header(message, MessageHeaders.CORRELATION_ID);
    if (correlationId != null && !correlationId.isBlank()) {
      MDC.put("correlationId", correlationId);
    }
    try {
      switch (eventType) {
        case AppointmentEventTypes.CANCELLED -> {
          AppointmentCancelledPayload payload = read(message, AppointmentCancelledPayload.class);
          reservationService.release(new ReleaseSlotCommand(payload.appointmentId()));
        }
        case AppointmentEventTypes.RESCHEDULED -> {
          AppointmentRescheduledPayload payload =
              read(message, AppointmentRescheduledPayload.class);
          reservationService.release(new ReleaseSlotCommand(payload.appointmentId()));
        }
        default -> log.debug("Ignoring appointment event type={}", eventType);
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
    String value = header(message, MessageHeaders.EVENT_ID);
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
