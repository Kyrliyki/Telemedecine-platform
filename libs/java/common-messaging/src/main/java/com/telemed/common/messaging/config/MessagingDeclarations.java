package com.telemed.common.messaging.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;

/**
 * Builds an opt-in durable consumer queue with a dead-letter queue.
 *
 * <p>Queue and dead-letter names default to {@code <service>.events} and {@code
 * <service>.events.dlq}. The queue is bound to the service exchange with the configured routing key
 * (default {@code #}).
 */
public final class MessagingDeclarations {

  private MessagingDeclarations() {}

  public static Declarables create(
      TopicExchange exchange,
      TopicExchange dlx,
      MessagingProperties.Queue properties,
      String serviceName) {
    String queueName =
        (properties.name() == null || properties.name().isBlank())
            ? serviceName + ".events"
            : properties.name();
    String deadLetterQueueName = queueName + ".dlq";
    String deadLetterRoutingKey =
        (properties.deadLetterRoutingKey() == null || properties.deadLetterRoutingKey().isBlank())
            ? deadLetterQueueName
            : properties.deadLetterRoutingKey();

    Queue queue =
        QueueBuilder.durable(queueName)
            .deadLetterExchange(dlx.getName())
            .deadLetterRoutingKey(deadLetterRoutingKey)
            .build();
    Queue deadLetterQueue = QueueBuilder.durable(deadLetterQueueName).build();

    Binding binding = BindingBuilder.bind(queue).to(exchange).with(properties.routingKey());
    Binding deadLetterBinding =
        BindingBuilder.bind(deadLetterQueue).to(dlx).with(deadLetterRoutingKey);

    return new Declarables(queue, deadLetterQueue, binding, deadLetterBinding);
  }
}
