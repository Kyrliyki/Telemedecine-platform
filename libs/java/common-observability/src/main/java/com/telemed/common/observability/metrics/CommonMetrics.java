package com.telemed.common.observability.metrics;


import com.telemed.common.observability.config.ObservabilityProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

public class CommonMetrics {

    private final MeterRegistry registry;
    private final String service;
    private final String environment;

    public CommonMetrics(MeterRegistry registry, ObservabilityProperties props) {
        this.registry = registry;
        this.service = props.service();
        this.environment = props.environment();
    }


    public Counter counter(String name, String... tags) {
        requireEvenTags(tags);
        var builder = Counter.builder(name)
                .tag("service", service)
                .tag("environment", environment);
        for (int i = 0; i < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        return builder.register(registry);
    }

    public Timer timer(String name, String... tags) {
        requireEvenTags(tags);
        var builder = Timer.builder(name)
                .tag("service", service)
                .tag("environment", environment);
        for (int i = 0; i < tags.length; i += 2) {
            builder.tag(tags[i], tags[i + 1]);
        }
        return builder.register(registry);
    }

    private static void requireEvenTags(String... tags) {
        if (tags.length % 2 != 0) {
            throw new IllegalArgumentException("Tags must be provided as key/value pairs");
        }
    }
}
