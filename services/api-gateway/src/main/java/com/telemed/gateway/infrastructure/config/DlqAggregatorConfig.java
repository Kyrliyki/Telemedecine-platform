package com.telemed.gateway.infrastructure.config;

import com.telemed.gateway.infrastructure.dlq.DlqAggregatorService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GatewayDlqProperties.class)
public class DlqAggregatorConfig {

  @Bean
  public DlqAggregatorService dlqAggregatorService(GatewayDlqProperties properties) {
    return new DlqAggregatorService(properties);
  }
}
