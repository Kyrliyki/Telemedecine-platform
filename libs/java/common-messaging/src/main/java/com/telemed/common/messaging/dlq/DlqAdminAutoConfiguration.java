package com.telemed.common.messaging.dlq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Adds {@code /internal/dlq} to every servlet service with RabbitMQ support. Services without
 * consumer queues expose an empty catalog.
 */
@AutoConfiguration
@ConditionalOnClass(RabbitTemplate.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class DlqAdminAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public DlqAdminService dlqAdminService(
      RabbitTemplate rabbitTemplate, ObjectProvider<DlqQueueCatalog> catalogProvider) {
    DlqQueueCatalog catalog =
        catalogProvider.getIfAvailable(() -> new DlqQueueCatalog(java.util.List.of()));
    return new DlqAdminService(rabbitTemplate, catalog);
  }

  @Bean
  @ConditionalOnMissingBean
  public DlqAdminController dlqAdminController(DlqAdminService service) {
    return new DlqAdminController(service);
  }
}
