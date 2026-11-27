package com.telemed.common.observability.metrics;

import com.telemed.common.observability.config.ObservabilityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommonMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CommonMetrics metrics = new CommonMetrics(
            registry, new ObservabilityProperties("auth-service", "test"));

    @Test
    void registersCounterWithServiceTags() {
        metrics.counter("test_events_total", "type", "login").increment();

        var counter = registry.get("test_events_total")
                .tag("service", "auth-service")
                .tag("environment", "test")
                .tag("type", "login")
                .counter();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void rejectsUnpairedTags() {
        assertThatThrownBy(() -> metrics.counter("broken_total", "key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> metrics.timer("broken_timer", "key"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
