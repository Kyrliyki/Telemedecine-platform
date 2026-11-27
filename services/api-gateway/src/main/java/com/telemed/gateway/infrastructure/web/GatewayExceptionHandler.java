package com.telemed.gateway.infrastructure.web;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webflux.error.ErrorWebExceptionHandler;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Last-resort error mapper for failures raised outside the gateway filter chain, such as routing
 * errors or a closed downstream connection.
 */
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler, Ordered {

  private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable exception) {
    if (exchange.getResponse().isCommitted()) {
      return Mono.error(exception);
    }
    if (exception instanceof ResponseStatusException statusException) {
      HttpStatus status = HttpStatus.resolve(statusException.getStatusCode().value());
      if (status != null && status.is4xxClientError()) {
        return GatewayProblemWriter.write(
            exchange,
            status,
            "DOWNSTREAM_ERROR",
            statusException.getReason() == null
                ? status.getReasonPhrase()
                : statusException.getReason());
      }
    }
    if (isUnavailable(exception)) {
      log.warn(
          "gateway_unhandled_fallback path={} reason={}",
          exchange.getRequest().getURI().getRawPath(),
          exception.toString());
      return GatewayProblemWriter.write(
          exchange,
          HttpStatus.SERVICE_UNAVAILABLE,
          "SERVICE_UNAVAILABLE",
          "The requested service is temporarily unavailable");
    }
    log.error(
        "gateway_unhandled_error path={}", exchange.getRequest().getURI().getRawPath(), exception);
    return GatewayProblemWriter.write(
        exchange, HttpStatus.INTERNAL_SERVER_ERROR, "GATEWAY_ERROR", "Unexpected gateway error");
  }

  private static boolean isUnavailable(Throwable exception) {
    Throwable current = exception;
    int depth = 0;
    while (current != null && depth++ < 12) {
      if (current instanceof IOException || current instanceof TimeoutException) {
        return true;
      }
      String name = current.getClass().getName();
      if (name.contains("NotFoundException")
          || name.contains("WebClientRequestException")
          || name.contains("PrematureCloseException")) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE;
  }
}
