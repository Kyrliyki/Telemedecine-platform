package com.telemed.common.messaging.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class MessagingDeclarationsTest {

  @Test
  void createsQueueDeadLetterQueueAndBindings() {
    TopicExchange exchange = new TopicExchange("auth.events");
    TopicExchange dlx = new TopicExchange("auth.events.dlx");

    Declarables declarations =
        MessagingDeclarations.create(
            exchange,
            dlx,
            new MessagingProperties.Queue(true, "billing.events", "billing.#", null),
            "billing-service");

    var queues = declarations.getDeclarablesByType(Queue.class);
    assertThat(queues)
        .extracting(Queue::getName)
        .containsExactlyInAnyOrder("billing.events", "billing.events.dlq");

    var bindings = declarations.getDeclarablesByType(Binding.class);
    assertThat(bindings).hasSize(2);
    assertThat(bindings)
        .extracting(Binding::getRoutingKey)
        .containsExactlyInAnyOrder("billing.#", "billing.events.dlq");
  }

  @Test
  void usesServiceNameWhenQueueNameIsMissing() {
    Declarables declarations =
        MessagingDeclarations.create(
            new TopicExchange("auth.events"),
            new TopicExchange("auth.events.dlx"),
            new MessagingProperties.Queue(true, null, null, null),
            "auth-service");

    assertThat(declarations.getDeclarablesByType(Queue.class))
        .extracting(Queue::getName)
        .contains("auth-service.events", "auth-service.events.dlq");
  }
}
