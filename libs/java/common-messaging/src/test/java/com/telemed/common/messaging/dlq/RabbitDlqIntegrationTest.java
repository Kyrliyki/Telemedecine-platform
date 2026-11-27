package com.telemed.common.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemed.common.events.AuthEventTypes;
import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.common.messaging.publisher.RabbitEventPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RabbitDlqIntegrationTest {

  @Container
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

  private CachingConnectionFactory connectionFactory;
  private RabbitTemplate template;
  private RabbitAdmin admin;

  @BeforeEach
  void setUp() {
    connectionFactory = new CachingConnectionFactory(RABBIT.getHost(), RABBIT.getAmqpPort());
    connectionFactory.setUsername(RABBIT.getAdminUsername());
    connectionFactory.setPassword(RABBIT.getAdminPassword());
    connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
    connectionFactory.setPublisherReturns(true);
    template = new RabbitTemplate(connectionFactory);
    template.setMandatory(true);
    admin = new RabbitAdmin(connectionFactory);
  }

  @Test
  void replaysMessageFromRealDlqBackToExchange() {
    String exchange = "test.auth.events";
    String queue = "test.user.events";
    String dlq = "test.user.events.dlq";
    admin.declareExchange(new TopicExchange(exchange));
    admin.declareQueue(QueueBuilder.durable(queue).build());
    admin.declareQueue(QueueBuilder.durable(dlq).build());
    admin.declareBinding(
        BindingBuilder.bind(new org.springframework.amqp.core.Queue(queue))
            .to(new TopicExchange(exchange))
            .with("auth.#"));

    DlqAdminService service =
        new DlqAdminService(
            template,
            new DlqQueueCatalog(
                List.of(new DlqQueueDefinition("auth", dlq, exchange, "integration"))));

    MessageProperties properties = new MessageProperties();
    properties.setHeader(MessageHeaders.EVENT_TYPE, AuthEventTypes.USER_REGISTERED);
    Message message =
        new Message(
            "{\"userId\":\"00000000-0000-0000-0000-000000000000\"}"
                .getBytes(StandardCharsets.UTF_8),
            properties);
    template.send("", dlq, message);

    assertThat(service.list().getFirst().messages()).isEqualTo(1);
    DlqReplayResult result = service.replay("auth", 10, false);

    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    Message replayed = receive(queue);
    assertThat(replayed).isNotNull();
    assertThat(replayed.getMessageProperties().getHeaders()).doesNotContainKey("x-death");
  }

  @Test
  void publisherPublishesEnvelopeToBoundQueue() {
    String exchange = "test.publisher.events";
    String queue = "test.publisher.queue";
    admin.declareExchange(new TopicExchange(exchange));
    admin.declareQueue(QueueBuilder.durable(queue).build());
    admin.declareBinding(
        BindingBuilder.bind(new org.springframework.amqp.core.Queue(queue))
            .to(new TopicExchange(exchange))
            .with("test.#"));

    RabbitEventPublisher publisher =
        new RabbitEventPublisher(template, exchange, Duration.ofSeconds(5));
    IntegrationEventEnvelope envelope =
        new IntegrationEventEnvelope(
            UUID.randomUUID(), "test.event.v1", Instant.now(), "correlation-1", "{\"value\":1}", 1);

    publisher.publish(envelope);
    Message received = receive(queue);

    assertThat(received).isNotNull();
    Object eventType = received.getMessageProperties().getHeader(MessageHeaders.EVENT_TYPE);
    assertThat(eventType).isEqualTo("test.event.v1");
  }

  private Message receive(String queue) {
    for (int i = 0; i < 50; i++) {
      Message message = template.receive(queue, 100);
      if (message != null) {
        return message;
      }
    }
    return null;
  }
}
