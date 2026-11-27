package com.telemed.common.outbox.config;

import com.telemed.common.events.EventPublisher;
import com.telemed.common.outbox.cleanup.OutboxCleanupJob;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.metrics.OutboxMetrics;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.common.outbox.relay.OutboxPersistenceService;
import com.telemed.common.outbox.relay.OutboxRelay;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@AutoConfiguration(afterName = "com.telemed.common.messaging.config.MessagingAutoConfiguration")
@EnableConfigurationProperties(OutboxProperties.class)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "outbox",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxAutoConfiguration {

  @Bean
  @ConditionalOnBean({OutboxRepository.class, EventPublisher.class, OutboxEventFactory.class})
  @ConditionalOnMissingBean
  public OutboxRelay outboxRelay(
      OutboxPersistenceService persistence,
      EventPublisher publisher,
      OutboxProperties properties,
      OutboxMetrics metrics,
      OutboxEventFactory eventFactory,
      @Value("${spring.application.name:unknown}") String serviceName) {
    return new OutboxRelay(persistence, publisher, properties, metrics, eventFactory, serviceName);
  }

  @Bean
  @ConditionalOnBean(OutboxRepository.class)
  @ConditionalOnMissingBean
  public OutboxCleanupJob outboxCleanupJob(
      OutboxRepository repository,
      OutboxProperties properties,
      @Value("${spring.application.name:unknown}") String serviceName) {
    return new OutboxCleanupJob(repository, properties, serviceName);
  }

  @Bean
  @ConditionalOnMissingBean
  public OutboxMetrics outboxMetrics(
      MeterRegistry registry, @Value("${spring.application.name:unknown}") String serviceName) {
    return new OutboxMetrics(registry, serviceName);
  }

  @Bean
  @ConditionalOnMissingBean
  public OutboxPersistenceService outboxPersistenceService(OutboxRepository repository) {
    return new OutboxPersistenceService(repository);
  }
}
