package com.telemed.schedule.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ScheduleMetrics {

  private final Counter held;
  private final Counter confirmed;
  private final Counter released;
  private final Counter conflicts;
  private final Counter expired;

  public ScheduleMetrics(MeterRegistry registry) {
    this.held = Counter.builder("schedule_slots_held_total").register(registry);
    this.confirmed = Counter.builder("schedule_slots_confirmed_total").register(registry);
    this.released = Counter.builder("schedule_slots_released_total").register(registry);
    this.conflicts = Counter.builder("schedule_slot_conflicts_total").register(registry);
    this.expired = Counter.builder("schedule_slots_expired_total").register(registry);
  }

  public void held() {
    held.increment();
  }

  public void confirmed() {
    confirmed.increment();
  }

  public void released() {
    released.increment();
  }

  public void conflict() {
    conflicts.increment();
  }

  public void expired() {
    expired.increment();
  }
}
