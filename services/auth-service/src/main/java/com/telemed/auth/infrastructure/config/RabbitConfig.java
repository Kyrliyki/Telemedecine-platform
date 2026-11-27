package com.telemed.auth.infrastructure.config;

import com.telemed.common.messaging.dlq.DlqQueueCatalog;
import java.util.List;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

  public static final String EXCHANGE = "auth.events";
  public static final String DLX = "auth.events.dlx";

  @Bean
  public DlqQueueCatalog dlqQueueCatalog() {

    return new DlqQueueCatalog(List.of());
  }

  @Bean
  public TopicExchange authEventsExchange() {
    return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
  }

  @Bean
  public TopicExchange authEventsDlx() {
    return ExchangeBuilder.topicExchange(DLX).durable(true).build();
  }

  @Bean
  public JacksonJsonMessageConverter jacksonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  @Bean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, JacksonJsonMessageConverter converter) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    template.setMandatory(true);
    template.setConfirmCallback((correlation, ack, reason) -> {});

    return template;
  }
}
