package com.telemed.emr.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class EmrMetrics {

  private final Counter created;
  private final Counter finalized;
  private final Counter amended;
  private final Counter deleted;

  public EmrMetrics(MeterRegistry registry) {
    this.created = Counter.builder("emr_records_created_total").register(registry);
    this.finalized = Counter.builder("emr_records_finalized_total").register(registry);
    this.amended = Counter.builder("emr_records_amended_total").register(registry);
    this.deleted = Counter.builder("emr_records_deleted_total").register(registry);
  }

  public void created() {
    created.increment();
  }

  public void finalized() {
    finalized.increment();
  }

  public void amended() {
    amended.increment();
  }

  public void deleted(int count) {
    deleted.increment(count);
  }
}
