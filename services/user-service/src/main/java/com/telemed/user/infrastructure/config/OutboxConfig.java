package com.telemed.user.infrastructure.config;

import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.user.infrastructure.persistence.entity.UserOutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

  @Bean
  public OutboxEventFactory outboxEventFactory(
      @Value("${spring.application.name}") String serviceName) {
    return new OutboxEventFactory(serviceName, UserOutboxEvent::new);
  }
}
