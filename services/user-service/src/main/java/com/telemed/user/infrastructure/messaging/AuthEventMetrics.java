package com.telemed.user.infrastructure.messaging;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class AuthEventMetrics {

  private final MeterRegistry registry;
  private final AtomicLong lastSuccessEpochSeconds = new AtomicLong(0);

  public AuthEventMetrics(MeterRegistry registry) {
    this.registry = registry;
    Gauge.builder("user.auth.events.last.success", lastSuccessEpochSeconds, AtomicLong::get)
        .description("Unix timestamp of the last successfully processed auth event")
        .register(registry);
  }

  public void recordSuccess(String eventType) {
    counter("success", eventType).increment();
    lastSuccessEpochSeconds.set(System.currentTimeMillis() / 1000);
  }

  public void recordDuplicate(String eventType) {
    counter("duplicate", eventType).increment();
  }

  public void recordFailure(String eventType) {
    counter("failure", eventType).increment();
  }

  private Counter counter(String result, String eventType) {
    return Counter.builder("user.auth.events")
        .description("Auth events handled by user-service")
        .tag("result", result)
        .tag("event_type", eventType == null ? "unknown" : eventType)
        .register(registry);
  }
}
