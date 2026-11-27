package com.telemed.gateway.infrastructure.filter;

import com.telemed.gateway.infrastructure.web.GatewayProblemWriter;
import java.io.IOException;
import java.net.ConnectException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Converts downstream connection/timeout failures into stable problem responses instead of generic
 * 500 pages.
 *
 * <p>This is the gateway-side fallback used for services that are not deployed yet (Python services
 * are added incrementally) and for transient outages. The response shape is identical for every
 * downstream service, which lets clients implement one retry/error policy.
 */
@Component
public class GatewayFallbackFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(GatewayFallbackFilter.class);

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return chain
        .filter(exchange)
        .onErrorResume(
            exception -> {
              if (!isDownstreamUnavailable(exception)) {
                return Mono.error(exception);
              }
              log.warn(
                  "gateway_fallback path={} method={} reason={}",
                  exchange.getRequest().getURI().getRawPath(),
                  exchange.getRequest().getMethod(),
                  rootMessage(exception));
              return GatewayProblemWriter.write(
                  exchange,
                  HttpStatus.SERVICE_UNAVAILABLE,
                  "SERVICE_UNAVAILABLE",
                  "The requested service is temporarily unavailable");
            });
  }

  private static boolean isDownstreamUnavailable(Throwable exception) {
    Throwable current = exception;
    int depth = 0;
    while (current != null && depth++ < 12) {
      if (current instanceof ConnectException
          || current instanceof TimeoutException
          || current instanceof IOException
          || current instanceof ResponseStatusException statusException
              && statusException.getStatusCode().value() == 503) {
        return true;
      }
      String name = current.getClass().getName();
      if (name.contains("WebClientRequestException")
          || name.contains("PrematureCloseException")
          || name.contains("AnnotatedConnectException")
          || name.contains("NotFoundException")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  private static String rootMessage(Throwable exception) {
    Throwable current = exception;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String message = current.getMessage();
    return message == null || message.isBlank()
        ? current.getClass().getSimpleName()
        : message.length() > 240 ? message.substring(0, 240) : message;
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE - 10;
  }
}
