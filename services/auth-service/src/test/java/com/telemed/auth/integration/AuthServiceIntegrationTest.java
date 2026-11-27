package com.telemed.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "security.internal.enabled=false",
      "outbox.enabled=true",
      "auth.bootstrap-admin.enabled=true",
      "auth.bootstrap-admin.email=admin@test.local",
      "auth.bootstrap-admin.password=Root123456789!"
    })
class AuthServiceIntegrationTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("telemedicine")
          .withUsername("telemedicine")
          .withPassword("telemedicine");

  @Container
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.hikari.schema", () -> "auth_service");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "auth_service");
    registry.add("spring.flyway.schemas", () -> "auth_service");
    registry.add("spring.flyway.default-schema", () -> "auth_service");
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
  }

  @Value("${local.server.port}")
  private int port;

  @org.springframework.beans.factory.annotation.Autowired private JdbcTemplate jdbc;

  private RestClient rest;

  @BeforeEach
  void setUpClient() {
    rest = RestClient.create("http://localhost:" + port);
  }

  @Test
  void logsInAndReadsPersistentAudit() {
    Map tokens = login();
    String accessToken = (String) tokens.get("accessToken");
    assertThat(accessToken).isNotBlank();

    ResponseEntity<Map> audit =
        rest.get()
            .uri("/api/v1/admin/auth/audit?page=0&size=20")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .toEntity(Map.class);

    assertThat(audit.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Integer) audit.getBody().get("totalElements")).isGreaterThanOrEqualTo(1);

    expectUnauthorized(
        () ->
            rest.post()
                .uri("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", "admin@test.local", "password", "wrong-password"))
                .retrieve()
                .toBodilessEntity());

    ResponseEntity<Map> auditAfterFailure =
        rest.get()
            .uri("/api/v1/admin/auth/audit?action=LOGIN_FAILED&page=0&size=20")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .toEntity(Map.class);
    assertThat(auditAfterFailure.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((Integer) auditAfterFailure.getBody().get("totalElements"))
        .isGreaterThanOrEqualTo(1);
  }

  @Test
  void logoutRevokesAccessAndRefreshTokens() {
    Map tokens = login();
    String accessToken = (String) tokens.get("accessToken");
    String refreshToken = (String) tokens.get("refreshToken");

    rest.post()
        .uri("/api/v1/auth/logout")
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .body(Map.of("refreshToken", refreshToken))
        .retrieve()
        .toBodilessEntity();

    expectUnauthorized(
        () ->
            rest.get()
                .uri("/api/v1/admin/auth/audit")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity());

    expectUnauthorized(
        () ->
            rest.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toBodilessEntity());
  }

  @Test
  void refreshRotationRejectsReusedRefreshToken() {
    Map tokens = login();
    String refreshToken = (String) tokens.get("refreshToken");

    ResponseEntity<Map> refreshed =
        rest.post()
            .uri("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("refreshToken", refreshToken))
            .retrieve()
            .toEntity(Map.class);

    assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat((String) refreshed.getBody().get("accessToken")).isNotBlank();

    expectUnauthorized(
        () ->
            rest.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toBodilessEntity());
  }

  @Test
  void roleChangeRevokesRefreshTokens() {
    Map tokens = login();
    String accessToken = (String) tokens.get("accessToken");
    String refreshToken = (String) tokens.get("refreshToken");
    String adminId = adminUserId(accessToken);

    rest.post()
        .uri("/api/v1/admin/users/{id}/roles", adminId)
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(accessToken))
        .body(Map.of("role", "DOCTOR"))
        .retrieve()
        .toBodilessEntity();

    expectUnauthorized(
        () ->
            rest.post()
                .uri("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("refreshToken", refreshToken))
                .retrieve()
                .toBodilessEntity());

    assertThat((String) login().get("accessToken")).isNotBlank();
  }

  @Test
  void registrationVerifiesEmailAndLogsIn() {
    String email = "verify-" + UUID.randomUUID() + "@test.local";
    String password = "Test123456789!";

    rest.post()
        .uri("/api/v1/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "password", password))
        .retrieve()
        .toBodilessEntity();

    String payload = latestOutboxPayload("auth.user.email_verification_requested.v1");
    String token = JsonPath.read(payload, "$.rawToken");

    rest.post()
        .uri("/api/v1/auth/verify-email")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("token", token))
        .retrieve()
        .toBodilessEntity();

    Map tokens = login(email, password);
    assertThat((String) tokens.get("accessToken")).isNotBlank();
  }

  @Test
  void invitationAcceptanceCreatesActiveDoctor() {
    Map adminTokens = login();
    String adminAccess = (String) adminTokens.get("accessToken");
    String email = "invite-" + UUID.randomUUID() + "@test.local";
    String password = "Doctor123456789!";

    rest.post()
        .uri("/api/v1/admin/users/invite")
        .contentType(MediaType.APPLICATION_JSON)
        .headers(headers -> headers.setBearerAuth(adminAccess))
        .body(Map.of("email", email, "roles", List.of("DOCTOR")))
        .retrieve()
        .toBodilessEntity();

    String payload = latestOutboxPayload("auth.user.invited.v1");
    String token = JsonPath.read(payload, "$.rawToken");

    rest.post()
        .uri("/api/v1/auth/accept-invitation")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("token", token, "password", password))
        .retrieve()
        .toBodilessEntity();

    Map tokens = login(email, password);
    assertThat((String) tokens.get("accessToken")).isNotBlank();
  }

  private String latestOutboxPayload(String eventType) {
    return jdbc.queryForObject(
        "select payload from auth_service.outbox_event "
            + "where event_type = ? order by created_at desc limit 1",
        String.class,
        eventType);
  }

  private Map login() {
    return login("admin@test.local", "Root123456789!");
  }

  private Map login(String email, String password) {
    return rest.post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("email", email, "password", password))
        .retrieve()
        .toEntity(Map.class)
        .getBody();
  }

  @SuppressWarnings("unchecked")
  private String adminUserId(String accessToken) {
    ResponseEntity<Map> users =
        rest.get()
            .uri("/api/v1/admin/users?email=admin@test.local")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .retrieve()
            .toEntity(Map.class);
    List<Map> content = (List<Map>) users.getBody().get("content");
    return content.getFirst().get("userId").toString();
  }

  private static void expectUnauthorized(Runnable request) {
    try {
      request.run();
      fail("Expected 401");
    } catch (HttpClientErrorException exception) {
      assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
  }
}
