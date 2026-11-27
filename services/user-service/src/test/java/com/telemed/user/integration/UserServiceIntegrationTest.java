package com.telemed.user.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.telemed.common.events.AuthEventTypes;
import com.telemed.common.messaging.dlq.DlqAdminService;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.user.application.port.output.UserRepository;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.infrastructure.config.RabbitConfig;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
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
    properties = {
      "user.auth-service.reconciliation-enabled=false",
      "security.internal.enabled=false"
    })
class UserServiceIntegrationTest {

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
    registry.add("spring.datasource.hikari.schema", () -> "user_service");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "user_service");
    registry.add("spring.flyway.schemas", () -> "user_service");
    registry.add("spring.flyway.default-schema", () -> "user_service");
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
  }

  @Autowired private UserRepository userRepository;

  @Autowired private RabbitTemplate rabbitTemplate;

  @Autowired private DlqAdminService dlqAdminService;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.execute("DELETE FROM user_service.doctor_profiles");
    jdbcTemplate.execute("DELETE FROM user_service.user_roles");
    jdbcTemplate.execute("DELETE FROM user_service.users");
    jdbcTemplate.execute("DELETE FROM user_service.processed_event");
  }

  @Test
  void consumesRegisteredEventAndDeduplicatesRedelivery() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();

    publish(eventId, AuthEventTypes.USER_REGISTERED, registeredBody(userId));

    User user = awaitProfile(userId);
    assertThat(user.getEmail().value()).isEqualTo("integration@example.com");
    assertThat(user.getRoles()).containsExactly(UserRole.PATIENT);

    publish(eventId, AuthEventTypes.USER_REGISTERED, registeredBody(userId));
    Thread.sleep(1_000);

    Integer processed =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM user_service.processed_event WHERE event_id = ?",
            Integer.class,
            eventId);
    assertThat(processed).isEqualTo(1);
  }

  @Test
  void replaysAuthDlqThroughUnifiedAdminService() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    dlqAdminService.purge("auth");

    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setHeader(MessageHeaders.EVENT_ID, eventId.toString());
    properties.setHeader(MessageHeaders.EVENT_TYPE, AuthEventTypes.USER_REGISTERED);
    properties.setHeader(MessageHeaders.EVENT_VERSION, 1);
    rabbitTemplate.send(
        "",
        RabbitConfig.AUTH_DLQ,
        new Message(registeredBody(userId).getBytes(StandardCharsets.UTF_8), properties));

    var before = dlqAdminService.list().stream().filter(q -> q.key().equals("auth")).findFirst();
    assertThat(before).isPresent();
    assertThat(before.orElseThrow().messages()).isEqualTo(1);

    var result = dlqAdminService.replay("auth", 10, false);

    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    User user = awaitProfile(userId);
    assertThat(user.getEmail().value()).isEqualTo("integration@example.com");
  }

  @Test
  void rejectsStaleProfileUpdateWithOptimisticLocking() throws Exception {
    UUID userId = UUID.randomUUID();
    publish(UUID.randomUUID(), AuthEventTypes.USER_REGISTERED, registeredBody(userId));
    User first = awaitProfile(userId);
    User stale = userRepository.findById(new UserId(userId)).orElseThrow();

    first.updateProfile("First", "Writer", java.time.Instant.now());
    userRepository.save(first);

    stale.updateProfile("Stale", "Writer", java.time.Instant.now().plusSeconds(1));
    assertThatThrownBy(() -> userRepository.save(stale))
        .isInstanceOf(org.springframework.orm.ObjectOptimisticLockingFailureException.class);
  }

  private void publish(UUID eventId, String eventType, String body) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setMessageId(eventId.toString());
    properties.setHeader(MessageHeaders.EVENT_ID, eventId.toString());
    properties.setHeader(MessageHeaders.EVENT_TYPE, eventType);
    properties.setHeader(MessageHeaders.EVENT_VERSION, 1);
    properties.setHeader(MessageHeaders.CORRELATION_ID, "integration-test");
    rabbitTemplate.send(
        RabbitConfig.AUTH_EXCHANGE,
        eventType,
        new Message(body.getBytes(StandardCharsets.UTF_8), properties));
  }

  private User awaitProfile(UUID userId) throws InterruptedException {
    for (int attempt = 0; attempt < 60; attempt++) {
      Optional<User> found = userRepository.findById(new UserId(userId));
      if (found.isPresent()) {
        return found.get();
      }
      Thread.sleep(200);
    }
    return fail("Profile was not created from RabbitMQ event");
  }

  private static String registeredBody(UUID userId) {
    return """
        {
          "userId": "%s",
          "email": "integration@example.com",
          "roles": ["PATIENT"],
          "emailVerified": true,
          "source": "PASSWORD"
        }
        """
        .formatted(userId);
  }
}
