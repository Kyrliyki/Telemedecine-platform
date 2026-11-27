package com.telemed.emr.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.common.events.AppointmentEventTypes;
import com.telemed.common.messaging.dlq.DlqAdminService;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.emr.application.dto.command.CreateMedicalRecordCommand;
import com.telemed.emr.application.dto.command.ReplacePrescriptionsCommand;
import com.telemed.emr.application.dto.command.UpdateMedicalRecordCommand;
import com.telemed.emr.application.dto.command.UpsertVitalSignsCommand;
import com.telemed.emr.application.dto.view.EmrAuditView;
import com.telemed.emr.application.service.MedicalRecordService;
import com.telemed.emr.infrastructure.config.RabbitConfig;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"security.internal.enabled=false", "outbox.relay.interval=200"})
class EmrServiceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("telemedicine")
          .withUsername("telemedicine")
          .withPassword("telemedicine");

  @Container
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.hikari.schema", () -> "emr_service");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "emr_service");
    registry.add("spring.flyway.schemas", () -> "emr_service");
    registry.add("spring.flyway.default-schema", () -> "emr_service");
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
  }

  @Autowired private MedicalRecordService service;
  @Autowired private DlqAdminService dlqAdminService;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clean() {
    jdbcTemplate.execute("DELETE FROM emr_service.medical_attachment");
    jdbcTemplate.execute("DELETE FROM emr_service.vital_signs");
    jdbcTemplate.execute("DELETE FROM emr_service.prescription");
    jdbcTemplate.execute("DELETE FROM emr_service.medical_record");
    jdbcTemplate.execute("DELETE FROM emr_service.outbox_event");
    jdbcTemplate.execute("DELETE FROM emr_service.processed_event");
  }

  @Test
  void replaysDeadLetterQueueThroughUnifiedAdminService() {
    dlqAdminService.purge("appointment");
    UUID eventId = UUID.randomUUID();
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setHeader(MessageHeaders.EVENT_ID, eventId.toString());
    properties.setHeader(MessageHeaders.EVENT_TYPE, AppointmentEventTypes.CANCELLED);
    properties.setHeader(MessageHeaders.EVENT_VERSION, 1);
    rabbitTemplate.send(
        "",
        RabbitConfig.APPOINTMENT_DLQ,
        new Message(
            ("{\"appointmentId\":\"" + UUID.randomUUID() + "\"}").getBytes(StandardCharsets.UTF_8),
            properties));

    var queue =
        dlqAdminService.list().stream()
            .filter(item -> item.key().equals("appointment"))
            .findFirst();
    assertThat(queue).isPresent();
    assertThat(queue.orElseThrow().messages()).isEqualTo(1);

    var result = dlqAdminService.replay("appointment", 10, false);
    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
  }

  @Test
  void createsUpdatesFinalizesAndReadsRecord() {
    UUID doctorId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    UUID appointmentId = UUID.randomUUID();

    var created =
        service.create(
            doctorId,
            new CreateMedicalRecordCommand(
                patientId, appointmentId, "Headache", "G43", "Migraine", "No red flags", "Rest"));
    assertThat(created.status()).isEqualTo("DRAFT");

    service.update(
        created.id(),
        new UpdateMedicalRecordCommand(null, "G43.0", "Migraine without aura", null, null),
        doctorId,
        false);
    service.replacePrescriptions(
        created.id(),
        new ReplacePrescriptionsCommand(
            List.of(
                new ReplacePrescriptionsCommand.Entry(
                    "Ibuprofen", "400 mg", "3 times a day", 5, "After meals"))),
        doctorId,
        false);
    service.upsertVitalSigns(
        created.id(),
        new UpsertVitalSignsCommand(
            new BigDecimal("170"), new BigDecimal("70"), 120, 80, 72, new BigDecimal("36.6"), 98),
        doctorId,
        false);
    var finalized = service.finalizeRecord(created.id(), doctorId, false);

    assertThat(finalized.status()).isEqualTo("FINALIZED");
    assertThat(finalized.prescriptions()).hasSize(1);
    assertThat(finalized.vitalSigns()).isNotNull();

    var patientView = service.get(created.id(), patientId, Set.of("PATIENT"));
    assertThat(patientView.diagnosisCode()).isEqualTo("G43.0");

    assertThat(service.revisions(created.id(), doctorId, Set.of("DOCTOR")))
        .hasSizeGreaterThanOrEqualTo(4);
    assertThat(service.auditForRecord(created.id(), true)).isNotEmpty();
    assertThat(service.auditForRecord(created.id(), true))
        .extracting(EmrAuditView::action)
        .contains("RECORD_VIEWED");
    assertThatThrownBy(() -> service.get(created.id(), UUID.randomUUID(), Set.of("PATIENT")))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    assertThat(service.auditForRecord(created.id(), true))
        .extracting(EmrAuditView::action)
        .contains("ACCESS_DENIED");

    service.setLegalHold(created.id(), true, doctorId, true);
    assertThat(service.deleteByPatient(patientId)).isZero();

    assertThatThrownBy(
            () ->
                service.update(
                    created.id(),
                    new UpdateMedicalRecordCommand("x", null, null, null, null),
                    doctorId,
                    false))
        .isInstanceOf(IllegalStateException.class);

    Integer outbox =
        jdbcTemplate.queryForObject("SELECT count(*) FROM emr_service.outbox_event", Integer.class);
    assertThat(outbox).isGreaterThanOrEqualTo(4);
  }
}
