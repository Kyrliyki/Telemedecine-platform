package com.telemed.common.outbox.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;

public class OutboxMetrics {

  private final AtomicInteger pending = new AtomicInteger(0);
  private final Counter sent;
  private final Counter failed;
  private final Counter dead;

  public OutboxMetrics(MeterRegistry registry, String serviceName) {
    Gauge.builder("outbox_pending_total", pending, AtomicInteger::get)
        .tag("service", serviceName)
        .description("Outbox events pending in last batch")
        .register(registry);
    this.sent =
        Counter.builder("outbox_sent_total")
            .tag("service", serviceName)
            .description("Outbox events published")
            .register(registry);
    this.failed =
        Counter.builder("outbox_failed_total")
            .tag("service", serviceName)
            .description("Outbox events failed to publish (will retry)")
            .register(registry);
    this.dead =
        Counter.builder("outbox_dead_total")
            .tag("service", serviceName)
            .description("Outbox events moved to DEAD after max attempts")
            .register(registry);
  }

  public void recordPending(int count) {
    pending.set(count);
  }

  public void recordSent(int count) {
    if (count > 0) sent.increment(count);
  }

  public void recordFailed(int count) {
    if (count > 0) failed.increment(count);
  }

  public void recordDead(int count) {
    if (count > 0) dead.increment(count);
  }
}
