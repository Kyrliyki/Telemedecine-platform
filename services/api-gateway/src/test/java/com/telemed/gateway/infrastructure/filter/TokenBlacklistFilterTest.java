package com.telemed.gateway.infrastructure.filter;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.gateway.application.port.output.TokenBlacklistChecker;
import com.telemed.gateway.infrastructure.config.GatewayJwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

class TokenBlacklistFilterTest {

  private static final String USER_ID = UUID.randomUUID().toString();

  private TokenBlacklistChecker checker;
  private TokenBlacklistFilter filter;
  private GatewayFilterChain chain;
  private Jwt jwt;

  @BeforeEach
  void setUp() {
    checker = mock(TokenBlacklistChecker.class);
    GatewayJwtProperties properties =
        new GatewayJwtProperties(
            "auth-service",
            "http://localhost/jwks",
            List.of("telemedicine"),
            Duration.ofSeconds(30),
            true);
    filter = new TokenBlacklistFilter(checker, properties);
    chain = mock(GatewayFilterChain.class);
    jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .subject(USER_ID)
            .jti("jti-1")
            .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
            .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
            .build();
  }

  @Test
  void continuesForActiveToken() {
    when(checker.isBlacklisted("jti-1")).thenReturn(Mono.just(false));
    when(checker.isUserRevokedBefore(
            UUID.fromString(USER_ID), Instant.parse("2026-01-01T10:00:00Z")))
        .thenReturn(Mono.just(false));
    when(chain.filter(org.mockito.ArgumentMatchers.any())).thenReturn(Mono.empty());
    MockServerWebExchange exchange =
        MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/emr/records").build());

    withSecurity(filter.filter(exchange, chain));

    verify(chain).filter(exchange);
  }

  private void withSecurity(Mono<Void> result) {
    result
        .contextWrite(
            ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(jwt)))
        .block();
  }
}
