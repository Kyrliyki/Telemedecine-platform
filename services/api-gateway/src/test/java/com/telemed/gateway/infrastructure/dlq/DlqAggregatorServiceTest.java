package com.telemed.gateway.infrastructure.dlq;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.telemed.gateway.infrastructure.config.GatewayDlqProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DlqAggregatorServiceTest {

  private HttpServer server;
  private DlqAggregatorService service;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/internal/dlq",
        exchange -> {
          String body =
              """
              [{"key":"auth","queue":"user-service.auth.events.dlq",
                "exchange":"auth.events","description":"auth","messages":2,"available":true}]
              """;
          respond(exchange, body);
        });
    server.createContext(
        "/internal/dlq/auth/replay",
        exchange -> {
          String body =
              """
              {"queue":"auth","replayed":2,"failed":0,"dryRun":false,"candidates":2}
              """;
          respond(exchange, body);
        });
    server.start();
    service =
        new DlqAggregatorService(
            new GatewayDlqProperties(
                "internal-token",
                List.of(
                    new GatewayDlqProperties.Service(
                        "user-service", "http://localhost:" + server.getAddress().getPort()))));
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void aggregatesServiceDlqOverview() {
    DlqViews.DlqOverview overview = service.overview().block();

    assertThat(overview).isNotNull();
    assertThat(overview.services()).hasSize(1);
    assertThat(overview.services().getFirst().available()).isTrue();
    assertThat(overview.services().getFirst().queues().getFirst().messages()).isEqualTo(2);
  }

  @Test
  void replaysThroughServiceEndpoint() {
    DlqViews.DlqReplayResponse result = service.replay("user-service", "auth", 10, false).block();

    assertThat(result).isNotNull();
    assertThat(result.service()).isEqualTo("user-service");
    assertThat(result.queue()).isEqualTo("auth");
    assertThat(result.replayed()).isEqualTo(2);
    assertThat(result.failed()).isZero();
  }

  private static void respond(com.sun.net.httpserver.HttpExchange exchange, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
