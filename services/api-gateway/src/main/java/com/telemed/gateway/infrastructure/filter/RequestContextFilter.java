package com.telemed.gateway.infrastructure.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RequestContextFilter implements GlobalFilter, Ordered {

  public static final String REQUEST_ID = "X-Request-Id";
  public static final String CORRELATION_ID = "X-Correlation-Id";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String requestId = resolve(exchange.getRequest().getHeaders().getFirst(REQUEST_ID));
    String correlationId = resolve(exchange.getRequest().getHeaders().getFirst(CORRELATION_ID));

    exchange.getResponse().getHeaders().set(REQUEST_ID, requestId);
    exchange.getResponse().getHeaders().set(CORRELATION_ID, correlationId);

    String remoteAddress = resolveRemoteAddress(exchange);

    ServerHttpRequest request =
        exchange
            .getRequest()
            .mutate()
            .headers(
                headers -> {
                  headers.set(REQUEST_ID, requestId);
                  headers.set(CORRELATION_ID, correlationId);
                  if (remoteAddress != null) {
                    // Overwrite client-supplied values so downstream audit logs can trust it.
                    headers.set("X-Forwarded-For", remoteAddress);
                  }
                })
            .build();

    return chain.filter(exchange.mutate().request(request).build());
  }

  private static String resolve(String value) {
    return value == null || value.isBlank() ? UUID.randomUUID().toString() : value;
  }

  private static String resolveRemoteAddress(ServerWebExchange exchange) {
    var remoteAddress = exchange.getRequest().getRemoteAddress();
    if (remoteAddress == null || remoteAddress.getAddress() == null) {
      return null;
    }
    return remoteAddress.getAddress().getHostAddress();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
