package com.telemed.emr.infrastructure.messaging;

import com.telemed.common.events.AppointmentEventPayloads.AppointmentCompletedPayload;
import com.telemed.common.events.AppointmentEventTypes;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.emr.application.dto.command.CreateMedicalRecordCommand;
import com.telemed.emr.application.port.output.Clock;
import com.telemed.emr.application.service.MedicalRecordService;
import com.telemed.emr.infrastructure.config.RabbitConfig;
import com.telemed.emr.infrastructure.persistence.adapter.ProcessedEventRepositoryAdapter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
  private final MedicalRecordService recordService;
  private final ProcessedEventRepositoryAdapter processedEvents;
  private final Clock clock;

  public AppointmentEventConsumer(
      JsonMapper jsonMapper,
      MedicalRecordService recordService,
      ProcessedEventRepositoryAdapter processedEvents,
      Clock clock) {
    this.jsonMapper = jsonMapper;
    this.recordService = recordService;
    this.processedEvents = processedEvents;
    this.clock = clock;
  }

  @RabbitListener(queues = RabbitConfig.APPOINTMENT_QUEUE)
  @Transactional
  public void handle(Message message) {
    String eventType = header(message, MessageHeaders.EVENT_TYPE);
    UUID eventId = eventId(message);
    if (eventType == null || eventType.isBlank()) {
      log.warn("Appointment event without eventType, rejecting to DLQ");
      throw new AmqpRejectAndDontRequeueException("Appointment event without eventType");
    }
    if (eventId != null && !processedEvents.tryClaim(eventId, eventType, clock.now())) {
      log.info("Skipping duplicate appointment event eventId={} type={}", eventId, eventType);
      return;
    }
    if (!AppointmentEventTypes.COMPLETED.equals(eventType)) {
      log.debug("Ignoring appointment event type={}", eventType);
      return;
    }
    AppointmentCompletedPayload payload =
        jsonMapper.readValue(message.getBody(), AppointmentCompletedPayload.class);
    recordService.create(
        payload.doctorId(),
        new CreateMedicalRecordCommand(
            payload.patientId(), payload.appointmentId(), null, null, null, null, null));
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
