package com.telemed.gateway.infrastructure.filter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GatewayObservabilityFilter implements GlobalFilter, Ordered {

  private static final Logger accessLog = LoggerFactory.getLogger("gateway.access");

  private final MeterRegistry meterRegistry;

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    long start = System.nanoTime();

    return chain
        .filter(exchange)
        .doFinally(
            signal -> {
              Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
              String routeId = route == null ? "unmatched" : route.getId();
              String method = exchange.getRequest().getMethod().name();
              String path = exchange.getRequest().getURI().getRawPath();
              HttpStatusCode code = exchange.getResponse().getStatusCode();
              String status = code == null ? "UNKNOWN" : String.valueOf(code.value());
              String outcome = outcome(status);
              long durationNanos = System.nanoTime() - start;

              Timer.builder("gateway.requests.duration")
                  .tag("route", routeId)
                  .tag("method", method)
                  .tag("status", status)
                  .tag("outcome", outcome)
                  .register(meterRegistry)
                  .record(Duration.ofNanos(durationNanos));

              Counter.builder("gateway.requests.total")
                  .tag("route", routeId)
                  .tag("method", method)
                  .tag("status", status)
                  .tag("outcome", outcome)
                  .register(meterRegistry)
                  .increment();

              if (status.startsWith("5")) {
                Counter.builder("gateway.requests.errors")
                    .tag("route", routeId)
                    .tag("method", method)
                    .tag("status", status)
                    .register(meterRegistry)
                    .increment();
              }

              String requestId =
                  exchange.getRequest().getHeaders().getFirst(RequestContextFilter.REQUEST_ID);
              String correlationId =
                  exchange.getRequest().getHeaders().getFirst(RequestContextFilter.CORRELATION_ID);
              String userId =
                  exchange.getRequest().getHeaders().getFirst(IdentityHeadersFilter.USER_ID);

              accessLog.info(
                  "gateway_access method={} path={} route={} status={} outcome={} durationMs={} requestId={} correlationId={} userId={}",
                  method,
                  path,
                  routeId,
                  status,
                  outcome,
                  durationNanos / 1_000_000,
                  requestId,
                  correlationId,
                  userId);
            });
  }

  private static String outcome(String status) {
    if (status.startsWith("5")) {
      return "SERVER_ERROR";
    }
    if (status.startsWith("4")) {
      return "CLIENT_ERROR";
    }
    if ("UNKNOWN".equals(status)) {
      return "UNKNOWN";
    }
    return "SUCCESS";
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 30;
  }
}
