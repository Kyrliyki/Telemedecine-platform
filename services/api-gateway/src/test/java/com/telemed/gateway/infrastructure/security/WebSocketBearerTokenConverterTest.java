package com.telemed.gateway.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;

class WebSocketBearerTokenConverterTest {

  private final WebSocketBearerTokenConverter converter =
      new WebSocketBearerTokenConverter("/ws/chat/");

  @Test
  void readsTokenFromAuthorizationHeader() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer header-token")
                .build());

    assertThat(token(exchange)).isEqualTo("header-token");
  }

  @Test
  void readsTokenFromQueryParameterForWebSocketHandshake() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/ws/chat/rooms/1")
                .queryParam("token", "query-token")
                .build());

    assertThat(token(exchange)).isEqualTo("query-token");
  }

  @Test
  void prefersAuthorizationHeaderOverQueryParameter() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/ws/chat/rooms/1")
                .header(HttpHeaders.AUTHORIZATION, "Bearer header-token")
                .queryParam("token", "query-token")
                .build());

    assertThat(token(exchange)).isEqualTo("header-token");
  }

  @Test
  void ignoresQueryParameterForRegularRequests() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/v1/users/me")
                .queryParam("token", "query-token")
                .build());

    assertThat(authentication(exchange)).isNull();
  }

  @Test
  void ignoresBlankQueryParameter() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(
            MockServerHttpRequest.get("/ws/chat/rooms/1").queryParam("token", " ").build());

    assertThat(authentication(exchange)).isNull();
  }

  @Test
  void returnsEmptyWhenNoTokenIsPresent() {
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/ws/chat/rooms/1").build());

    assertThat(authentication(exchange)).isNull();
  }

  private Authentication authentication(MockServerWebExchange exchange) {
    return converter.convert(exchange).block();
  }

  private String token(MockServerWebExchange exchange) {
    Authentication authentication = authentication(exchange);
    assertThat(authentication).isInstanceOf(BearerTokenAuthenticationToken.class);
    return ((BearerTokenAuthenticationToken) authentication).getToken();
  }
}
