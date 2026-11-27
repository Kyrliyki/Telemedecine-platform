package com.telemed.emr.infrastructure.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EmrValidationClientsTest {

  private HttpServer server;
  private int port;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/internal/appointments",
        exchange -> {
          if (exchange.getRequestURI().getPath().contains("44444444")) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
          }
          String body =
              """
              {"appointmentId":"%s","patientId":"%s","doctorId":"%s","status":"COMPLETED"}
              """
                  .formatted(
                      UUID.fromString("11111111-2222-4333-8444-555555555555"),
                      UUID.fromString("22222222-3333-4444-8555-666666666666"),
                      UUID.fromString("33333333-4444-4555-8666-777777777777"));
          respond(exchange, body);
        });
    server.createContext(
        "/internal/files",
        exchange -> {
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    port = server.getAddress().getPort();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void appointmentClientReturnsAppointmentInfo() {
    AppointmentValidationClient client =
        new AppointmentValidationClient("http://localhost:" + port, "token", true);

    var result = client.find(UUID.fromString("11111111-2222-4333-8444-555555555555"));

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().status()).isEqualTo("COMPLETED");
  }

  @Test
  void appointmentClientTreats404AsMissing() {
    AppointmentValidationClient client =
        new AppointmentValidationClient("http://localhost:" + port, "token", true);

    assertThat(client.find(UUID.fromString("44444444-5555-4666-8777-888888888888"))).isEmpty();
  }

  @Test
  void disabledClientsDoNotCallDownstream() {
    UUID fileId = UUID.randomUUID();
    UUID patientId = UUID.randomUUID();
    AppointmentValidationClient appointment =
        new AppointmentValidationClient("http://localhost:" + port, "token", false);
    FileValidationClient file =
        new FileValidationClient("http://localhost:" + port, "token", false);

    assertThat(appointment.find(UUID.randomUUID())).isEmpty();
    file.validate(fileId, patientId);
  }

  @Test
  void fileClientCallsInternalMetadataEndpoint() {
    FileValidationClient file = new FileValidationClient("http://localhost:" + port, "token", true);

    file.validate(UUID.randomUUID(), UUID.randomUUID());
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
