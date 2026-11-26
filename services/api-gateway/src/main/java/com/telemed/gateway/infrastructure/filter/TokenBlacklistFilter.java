package com.telemed.gateway.infrastructure.filter;

import com.telemed.gateway.application.port.output.TokenBlacklistChecker;
import com.telemed.gateway.infrastructure.config.GatewayJwtProperties;
import com.telemed.gateway.infrastructure.web.GatewayProblemWriter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TokenBlacklistFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(TokenBlacklistFilter.class);

  private final TokenBlacklistChecker tokenBlacklistChecker;
  private final GatewayJwtProperties properties;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    if (!properties.blacklistEnabled()) {
      return chain.filter(exchange);
    }

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .filter(Authentication::isAuthenticated)
        .filter(authentication -> authentication.getPrincipal() instanceof Jwt)
        .map(authentication -> (Jwt) authentication.getPrincipal())
        .flatMap(jwt -> checkToken(exchange, chain, jwt))
        .switchIfEmpty(Mono.defer(() -> chain.filter(exchange)))
        .onErrorResume(
            exception -> {
              log.error("Token blacklist check failed", exception);
              return GatewayProblemWriter.write(
                  exchange,
                  HttpStatus.SERVICE_UNAVAILABLE,
                  "AUTH_BACKEND_UNAVAILABLE",
                  "Token revocation store is unavailable");
            });
  }

  private Mono<Void> checkToken(ServerWebExchange exchange, GatewayFilterChain chain, Jwt jwt) {
    String jti = jwt.getId();
    if (jti == null || jti.isBlank()) {
      return GatewayProblemWriter.write(
          exchange, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "Token has no jti claim");
    }
    Mono<Boolean> jtiCheck = tokenBlacklistChecker.isBlacklisted(jti);
    Mono<Boolean> userCheck =
        Mono.defer(
            () -> {
              UUID userId = subject(jwt);
              return tokenBlacklistChecker.isUserRevokedBefore(userId, jwt.getIssuedAt());
            });

    return Mono.zip(jtiCheck.defaultIfEmpty(false), userCheck.defaultIfEmpty(false))
        .flatMap(
            result -> {
              if (result.getT1()) {
                return GatewayProblemWriter.write(
                    exchange, HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED", "Token has been revoked");
              }
              if (result.getT2()) {
                return GatewayProblemWriter.write(
                    exchange,
                    HttpStatus.UNAUTHORIZED,
                    "TOKEN_REVOKED",
                    "Token was invalidated by an account or role change");
              }
              return chain.filter(exchange);
            });
  }

  private static UUID subject(Jwt jwt) {
    try {
      return jwt.getSubject() == null ? null : UUID.fromString(jwt.getSubject());
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 5;
  }
}
