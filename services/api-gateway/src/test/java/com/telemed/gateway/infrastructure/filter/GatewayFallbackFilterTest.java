package com.telemed.gateway.infrastructure.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class GatewayFallbackFilterTest {

  private final GatewayFallbackFilter filter = new GatewayFallbackFilter();

  @Test
  void convertsDownstreamConnectionFailureToStable503() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/chat/rooms").build());

    filter
        .filter(exchange, ignored -> Mono.error(new ConnectException("connection refused")))
        .block();

    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(exchange.getResponse().getBodyAsString().block())
        .contains("\"code\":\"SERVICE_UNAVAILABLE\"")
        .contains("\"path\":\"/api/v1/chat/rooms\"");
  }

  @Test
  void passesThroughUnrelatedErrors() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/emr/records").build());

    RuntimeException failure = new RuntimeException("application bug");
    try {
      filter.filter(exchange, ignored -> Mono.error(failure)).block();
    } catch (RuntimeException exception) {
      assertThat(exception).isSameAs(failure);
    }
  }
}
