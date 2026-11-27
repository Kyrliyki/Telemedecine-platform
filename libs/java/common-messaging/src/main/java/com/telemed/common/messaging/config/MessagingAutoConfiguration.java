package com.telemed.common.messaging.config;

import com.telemed.common.events.EventPublisher;
import com.telemed.common.messaging.health.RabbitHealthIndicator;
import com.telemed.common.messaging.publisher.RabbitEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(MessagingProperties.class)
@ConditionalOnProperty(
    prefix = "messaging",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MessagingAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(MessagingAutoConfiguration.class);

  @Bean
  @ConditionalOnMissingBean
  public TopicExchange serviceExchange(MessagingProperties props) {
    return ExchangeBuilder.topicExchange(props.exchange()).durable(true).build();
  }

  @Bean
  @ConditionalOnMissingBean
  public TopicExchange serviceDlx(MessagingProperties props) {
    return ExchangeBuilder.topicExchange(props.dlx()).durable(true).build();
  }

  @Bean
  @ConditionalOnProperty(prefix = "messaging.queue", name = "enabled", havingValue = "true")
  public Declarables messagingQueueDeclarations(
      MessagingProperties props, @Value("${spring.application.name:service}") String serviceName) {
    TopicExchange exchange = ExchangeBuilder.topicExchange(props.exchange()).durable(true).build();
    TopicExchange dlx = ExchangeBuilder.topicExchange(props.dlx()).durable(true).build();
    return MessagingDeclarations.create(exchange, dlx, props.queue(), serviceName);
  }

  @Bean
  @ConditionalOnMissingBean
  public RabbitTemplate rabbitTemplate(
      ConnectionFactory connectionFactory, MessageConverter converter, MessagingProperties props) {
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(converter);
    template.setMandatory(true);
    if (props.publisherConfirms()) {
      template.setConfirmCallback(
          (correlation, ack, reason) -> {
            if (!ack) {
              log.error(
                  "RabbitMQ publisher nack: correlationData={}, reason={}", correlation, reason);
            }
          });
    }

    template.setReturnsCallback(
        returned ->
            log.error(
                "RabbitMQ returned message: exchange={}, routingKey={}, "
                    + "replyCode={}, replyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText()));

    return template;
  }

  @Bean
  @ConditionalOnMissingBean
  public EventPublisher eventPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
    return new RabbitEventPublisher(rabbitTemplate, props.exchange(), props.confirmTimeout());
  }

  @Bean
  @ConditionalOnMissingBean
  public RabbitHealthIndicator rabbitHealthIndicator(RabbitTemplate rabbitTemplate) {
    return new RabbitHealthIndicator(rabbitTemplate);
  }
}
