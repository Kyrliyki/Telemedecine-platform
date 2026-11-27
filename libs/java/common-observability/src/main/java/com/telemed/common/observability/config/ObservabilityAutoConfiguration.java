package com.telemed.common.observability.config;

import com.telemed.common.observability.metrics.CommonMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@Import(ServletObservabilityConfiguration.class)
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public CommonMetrics commonMetrics(MeterRegistry registry,
                                       ObservabilityProperties props,
                                       @Value("${spring.application.name:unknown-service}") String appName) {
        var effective = new ObservabilityProperties(
                props.service().equals("unknown-service") ? appName : props.service(),
                props.environment()
        );
        return new CommonMetrics(registry, effective);
    }
}
