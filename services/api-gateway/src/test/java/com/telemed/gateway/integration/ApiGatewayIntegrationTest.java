package com.telemed.gateway.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemed.gateway.application.port.output.TokenBlacklistChecker;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayIntegrationTest {

  @Container
  static final GenericContainer<?> REDIS =
      new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

  private static final DisposableServer AUTH_STUB;
  private static final int AUTH_PORT;

  static {
    AUTH_STUB =
        HttpServer.create()
            .host("127.0.0.1")
            .port(0)
            .handle(
                (request, response) -> {
                  String body =
                      request.fullPath().startsWith("/oauth2/jwks")
                          ? "{\"keys\":[]}"
                          : "{\"accessToken\":\"stub\"}";
                  return response
                      .header("Content-Type", "application/json")
                      .sendString(Mono.just(body))
                      .then();
                })
            .bindNow();
    AUTH_PORT = AUTH_STUB.port();
  }

  @LocalServerPort private int port;

  @LocalManagementPort private int managementPort;

  @Autowired private TokenBlacklistChecker blacklistChecker;
  @Autowired private ReactiveStringRedisTemplate redis;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    registry.add("AUTH_SERVICE_URL", () -> "http://127.0.0.1:" + AUTH_PORT);
    registry.add("JWK_SET_URI", () -> "http://127.0.0.1:" + AUTH_PORT + "/oauth2/jwks");
    registry.add("JWT_ISSUER", () -> "auth-service");
    registry.add("JWT_AUDIENCE", () -> "telemedicine");
    registry.add("INTERNAL_SERVICE_TOKEN", () -> "test-internal-token");
    registry.add("FRONTEND_URL", () -> "http://localhost:3000");
    registry.add("MANAGEMENT_PORT", () -> "0");
  }

  @AfterAll
  static void stopAuthStub() {
    AUTH_STUB.disposeNow();
  }

  private WebTestClient client() {
    return WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void proxiesPublicAuthLoginToAuthService() {
    client()
        .post()
        .uri("/api/v1/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.accessToken")
        .isEqualTo("stub");
  }

  @Test
  void rejectsProtectedRouteWithoutToken() {
    client().get().uri("/api/v1/users/me").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void readsTokenBlacklistAndUserRevocationFromRedis() {
    String jti = UUID.randomUUID().toString();
    UUID userId = UUID.randomUUID();

    assertThat(blacklistChecker.isBlacklisted(jti).block()).isFalse();

    redis.opsForValue().set("blacklist:" + jti, "1").block();
    redis
        .opsForValue()
        .set("auth:revoked-before:" + userId, String.valueOf(Instant.now().toEpochMilli()))
        .block();

    assertThat(blacklistChecker.isBlacklisted(jti).block()).isTrue();
    assertThat(
            blacklistChecker
                .isUserRevokedBefore(userId, Instant.now().minusSeconds(60))
                .block())
        .isTrue();
  }

  @Test
  void exposesReadinessOnManagementPort() {
    WebTestClient managementClient =
        WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();

    byte[] body =
        managementClient
            .get()
            .uri("/actuator/health/readiness")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody()
            .returnResult()
            .getResponseBody();

    assertThat(new String(body)).contains("\"status\":\"UP\"");
  }
}
