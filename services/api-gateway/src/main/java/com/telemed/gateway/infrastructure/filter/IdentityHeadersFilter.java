package com.telemed.gateway.infrastructure.filter;

import com.telemed.gateway.application.port.input.GetCurrentUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class IdentityHeadersFilter implements GlobalFilter, Ordered {

  public static final String USER_ID = "X-User-Id";
  public static final String USER_ROLES = "X-User-Roles";
  public static final String USER_EMAIL = "X-User-Email";

  private final GetCurrentUserUseCase currentUserUseCase;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest sanitized =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  headers.remove(USER_ID);
                  headers.remove(USER_ROLES);
                  headers.remove(USER_EMAIL);
                })
            .build();
    ServerWebExchange sanitizedExchange = exchange.mutate().request(sanitized).build();

    return currentUserUseCase
        .currentUser()
        .map(
            user -> {
              ServerHttpRequest withIdentity =
                  sanitizedExchange
                      .getRequest()
                      .mutate()
                      .headers(
                          headers -> {
                            if (user.userId() != null) {
                              headers.set(USER_ID, user.userId());
                            }
                            if (!user.roles().isEmpty()) {
                              headers.set(USER_ROLES, String.join(",", user.roles()));
                            }
                            if (user.email() != null) {
                              headers.set(USER_EMAIL, user.email());
                            }
                          })
                      .build();
              return sanitizedExchange.mutate().request(withIdentity).build();
            })
        .defaultIfEmpty(sanitizedExchange)
        .flatMap(chain::filter);
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}
