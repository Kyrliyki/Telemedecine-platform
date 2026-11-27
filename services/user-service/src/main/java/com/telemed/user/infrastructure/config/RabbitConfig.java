package com.telemed.user.infrastructure.config;

import com.telemed.common.messaging.dlq.DlqQueueCatalog;
import com.telemed.common.messaging.dlq.DlqQueueDefinition;
import java.util.List;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class RabbitConfig {

  public static final String AUTH_EXCHANGE = "auth.events";
  public static final String AUTH_DLX = "auth.events.dlx";
  public static final String AUTH_QUEUE = "user-service.auth.events";
  public static final String AUTH_DLQ = AUTH_QUEUE + ".dlq";
  public static final String USER_EXCHANGE = "user.events";
  public static final String USER_DLX = "user.events.dlx";
  public static final String USER_EVENT_QUEUE = "user-service.events";
  public static final String USER_EVENT_DLQ = USER_EVENT_QUEUE + ".dlq";

  @Bean
  public DlqQueueCatalog dlqQueueCatalog() {
    return new DlqQueueCatalog(
        List.of(
            new DlqQueueDefinition(
                "auth", AUTH_DLQ, AUTH_EXCHANGE, "Auth events that user-service failed to consume"),
            new DlqQueueDefinition(
                "user-events",
                USER_EVENT_DLQ,
                USER_EXCHANGE,
                "User events returned undeliverable")));
  }

  @Bean
  public TopicExchange authEventsExchange() {
    return ExchangeBuilder.topicExchange(AUTH_EXCHANGE).durable(true).build();
  }

  @Bean
  public TopicExchange authEventsDlx() {
    return ExchangeBuilder.topicExchange(AUTH_DLX).durable(true).build();
  }

  @Bean
  public TopicExchange userEventsExchange() {
    return ExchangeBuilder.topicExchange(USER_EXCHANGE).durable(true).build();
  }

  @Bean
  public TopicExchange userEventsDlx() {
    return ExchangeBuilder.topicExchange(USER_DLX).durable(true).build();
  }

  @Bean
  public Queue userEventQueue() {
    return QueueBuilder.durable(USER_EVENT_QUEUE)
        .deadLetterExchange(USER_DLX)
        .deadLetterRoutingKey(USER_EVENT_DLQ)
        .build();
  }

  @Bean
  public Queue userEventDeadLetterQueue() {
    return QueueBuilder.durable(USER_EVENT_DLQ).build();
  }

  @Bean
  public Binding userEventBinding(Queue userEventQueue, TopicExchange userEventsExchange) {
    return BindingBuilder.bind(userEventQueue).to(userEventsExchange).with("user.#");
  }

  @Bean
  public Binding userEventDeadLetterBinding(
      Queue userEventDeadLetterQueue, TopicExchange userEventsDlx) {
    return BindingBuilder.bind(userEventDeadLetterQueue).to(userEventsDlx).with(USER_EVENT_DLQ);
  }

  @Bean
  public Queue userAuthQueue() {
    return QueueBuilder.durable(AUTH_QUEUE)
        .deadLetterExchange(AUTH_DLX)
        .deadLetterRoutingKey(AUTH_DLQ)
        .build();
  }

  @Bean
  public Queue userAuthDeadLetterQueue() {
    return QueueBuilder.durable(AUTH_DLQ).build();
  }

  @Bean
  public Binding userAuthBinding(Queue userAuthQueue, TopicExchange authEventsExchange) {
    return BindingBuilder.bind(userAuthQueue).to(authEventsExchange).with("auth.user.#");
  }

  @Bean
  public Binding userAuthDeadLetterBinding(
      Queue userAuthDeadLetterQueue, TopicExchange authEventsDlx) {
    return BindingBuilder.bind(userAuthDeadLetterQueue).to(authEventsDlx).with(AUTH_DLQ);
  }
}
