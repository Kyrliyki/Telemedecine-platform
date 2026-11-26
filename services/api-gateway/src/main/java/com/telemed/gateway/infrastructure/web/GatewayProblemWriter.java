package com.telemed.gateway.infrastructure.web;

import java.nio.charset.StandardCharsets;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public final class GatewayProblemWriter {

  private GatewayProblemWriter() {}

  public static Mono<Void> write(
      ServerWebExchange exchange, HttpStatus status, String code, String message) {
    var response = exchange.getResponse();
    if (response.isCommitted()) {
      return Mono.empty();
    }
    response.setStatusCode(status);
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
    String correlationId = exchange.getRequest().getHeaders().getFirst("X-Correlation-Id");
    String body =
        "{"
            + "\"status\":"
            + status.value()
            + ",\"code\":\""
            + escape(code)
            + "\""
            + ",\"message\":\""
            + escape(message)
            + "\""
            + ",\"path\":\""
            + escape(exchange.getRequest().getURI().getRawPath())
            + "\""
            + (requestId == null ? "" : ",\"requestId\":\"" + escape(requestId) + "\"")
            + (correlationId == null ? "" : ",\"correlationId\":\"" + escape(correlationId) + "\"")
            + "}";
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }

  private static String escape(String value) {
    if (value == null) {
      return "";
    }
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
