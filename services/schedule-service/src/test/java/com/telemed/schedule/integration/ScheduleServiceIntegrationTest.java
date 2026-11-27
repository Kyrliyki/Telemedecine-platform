package com.telemed.schedule.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemed.common.events.AuthEventTypes;
import com.telemed.common.messaging.dlq.DlqAdminService;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.schedule.application.dto.command.ReleaseSlotCommand;
import com.telemed.schedule.application.dto.command.ReserveSlotCommand;
import com.telemed.schedule.application.dto.command.UpdateWorkingHoursCommand;
import com.telemed.schedule.application.service.AvailabilityService;
import com.telemed.schedule.application.service.SlotReservationService;
import com.telemed.schedule.application.service.WorkingHoursService;
import com.telemed.schedule.infrastructure.config.RabbitConfig;
import com.telemed.schedule.infrastructure.persistence.entity.ScheduleOutboxEvent;
import com.telemed.schedule.infrastructure.persistence.repository.JpaScheduleOutboxRepository;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"security.internal.enabled=false", "outbox.relay.interval=3600000"})
class ScheduleServiceIntegrationTest {

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
    registry.add("spring.datasource.hikari.schema", () -> "schedule_service");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "schedule_service");
    registry.add("spring.flyway.schemas", () -> "schedule_service");
    registry.add("spring.flyway.default-schema", () -> "schedule_service");
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
  }

  @Autowired private WorkingHoursService workingHoursService;
  @Autowired private AvailabilityService availabilityService;
  @Autowired private SlotReservationService reservationService;
  @Autowired private DlqAdminService dlqAdminService;
  @Autowired private RabbitTemplate rabbitTemplate;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JpaScheduleOutboxRepository outboxRepository;
  @Autowired private PlatformTransactionManager transactionManager;

  @BeforeEach
  void clean() {
    jdbcTemplate.execute("DELETE FROM schedule_service.time_slot");
    jdbcTemplate.execute("DELETE FROM schedule_service.schedule_exception");
    jdbcTemplate.execute("DELETE FROM schedule_service.doctor_working_hours");
    jdbcTemplate.execute("DELETE FROM schedule_service.outbox_event");
    jdbcTemplate.execute("DELETE FROM schedule_service.processed_event");
  }

  @Test
  void replaysDeadLetterQueueThroughUnifiedAdminService() {
    dlqAdminService.purge("auth");
    UUID eventId = UUID.randomUUID();
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setHeader(MessageHeaders.EVENT_ID, eventId.toString());
    properties.setHeader(MessageHeaders.EVENT_TYPE, AuthEventTypes.USER_PASSWORD_CHANGED);
    properties.setHeader(MessageHeaders.EVENT_VERSION, 1);
    rabbitTemplate.send(
        "",
        RabbitConfig.AUTH_DLQ,
        new Message(
            ("{\"userId\":\"" + UUID.randomUUID() + "\"}").getBytes(StandardCharsets.UTF_8),
            properties));

    var queue =
        dlqAdminService.list().stream().filter(item -> item.key().equals("auth")).findFirst();
    assertThat(queue).isPresent();
    assertThat(queue.orElseThrow().messages()).isEqualTo(1);

    var result = dlqAdminService.replay("auth", 10, false);
    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
  }

  @Test
  void concurrentRelaysClaimDisjointOutboxEvents() throws Exception {
    for (int i = 0; i < 100; i++) {
      jdbcTemplate.update(
          """
          INSERT INTO schedule_service.outbox_event
            (id, event_id, event_type, service, payload, status, created_at, retry_count)
          VALUES (?, ?, 'schedule.test.v1', 'schedule-service', '{}', 'PENDING', now(), 0)
          """,
          UUID.randomUUID(),
          UUID.randomUUID());
    }

    TransactionTemplate transaction = new TransactionTemplate(transactionManager);
    CountDownLatch firstClaimed = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<List<ScheduleOutboxEvent>> first =
          executor.submit(
              () ->
                  transaction.execute(
                      status -> {
                        List<ScheduleOutboxEvent> claimed =
                            outboxRepository.claimPending(
                                "schedule-service",
                                List.of("PENDING", "FAILED"),
                                5,
                                java.time.Instant.now(),
                                java.time.Instant.now().minusSeconds(300),
                                50);
                        firstClaimed.countDown();
                        try {
                          releaseFirst.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException exception) {
                          Thread.currentThread().interrupt();
                        }
                        return claimed;
                      }));

      assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();
      Future<List<ScheduleOutboxEvent>> second =
          executor.submit(
              () ->
                  transaction.execute(
                      status ->
                          outboxRepository.claimPending(
                              "schedule-service",
                              List.of("PENDING", "FAILED"),
                              5,
                              java.time.Instant.now(),
                              java.time.Instant.now().minusSeconds(300),
                              50)));

      List<ScheduleOutboxEvent> secondClaimed = second.get(5, TimeUnit.SECONDS);
      releaseFirst.countDown();
      List<ScheduleOutboxEvent> firstClaimedEvents = first.get(5, TimeUnit.SECONDS);

      assertThat(firstClaimedEvents).hasSize(50);
      assertThat(secondClaimed).hasSize(50);
      assertThat(firstClaimedEvents)
          .extracting(ScheduleOutboxEvent::getEventId)
          .doesNotContainAnyElementsOf(
              secondClaimed.stream().map(ScheduleOutboxEvent::getEventId).toList());
    } finally {
      releaseFirst.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void concurrentHoldsForSameSlotAllowExactlyOneWinner() throws Exception {
    UUID doctorId = UUID.randomUUID();
    LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    Instant start = monday.atTime(9, 0).toInstant(ZoneOffset.UTC);
    workingHoursService.replace(
        doctorId,
        new UpdateWorkingHoursCommand(
            List.of(
                new UpdateWorkingHoursCommand.Entry(
                    1,
                    LocalTime.of(9, 0),
                    LocalTime.of(11, 0),
                    30,
                    0,
                    "UTC",
                    "STANDARD",
                    "Room 1")),
            monday,
            null));

    UUID firstAppointment = UUID.randomUUID();
    UUID secondAppointment = UUID.randomUUID();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch startSignal = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first =
          pool.submit(
              () -> {
                ready.countDown();
                startSignal.await();
                try {
                  reservationService.hold(
                      new ReserveSlotCommand(doctorId, start, firstAppointment));
                  return true;
                } catch (RuntimeException exception) {
                  return false;
                }
              });
      Future<Boolean> second =
          pool.submit(
              () -> {
                ready.countDown();
                startSignal.await();
                try {
                  reservationService.hold(
                      new ReserveSlotCommand(doctorId, start, secondAppointment));
                  return true;
                } catch (RuntimeException exception) {
                  return false;
                }
              });

      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      startSignal.countDown();

      boolean firstWon = first.get(15, TimeUnit.SECONDS);
      boolean secondWon = second.get(15, TimeUnit.SECONDS);
      assertThat(firstWon ^ secondWon).isTrue();
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void holdsConfirmsAndReleasesSlot() {
    UUID doctorId = UUID.randomUUID();
    LocalDate monday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    Instant start = monday.atTime(9, 0).toInstant(ZoneOffset.UTC);

    workingHoursService.replace(
        doctorId,
        new UpdateWorkingHoursCommand(
            List.of(
                new UpdateWorkingHoursCommand.Entry(
                    1,
                    LocalTime.of(9, 0),
                    LocalTime.of(11, 0),
                    30,
                    0,
                    "UTC",
                    "STANDARD",
                    "Room 1")),
            monday,
            null));

    var slots = availabilityService.availability(doctorId, monday, monday);
    assertThat(slots).hasSize(4);

    UUID appointmentId = UUID.randomUUID();
    var held = reservationService.hold(new ReserveSlotCommand(doctorId, start, appointmentId));
    assertThat(held.status().name()).isEqualTo("HELD");
    assertThat(held.location()).isEqualTo("Room 1");

    var confirmed = reservationService.confirm(appointmentId);
    assertThat(confirmed.status().name()).isEqualTo("CONFIRMED");

    assertThat(reservationService.release(new ReleaseSlotCommand(appointmentId))).isTrue();
    var afterRelease = availabilityService.availability(doctorId, monday, monday);
    assertThat(afterRelease.getFirst().status().name()).isEqualTo("AVAILABLE");

    Integer outbox =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM schedule_service.outbox_event", Integer.class);
    assertThat(outbox).isGreaterThanOrEqualTo(4);
  }
}
