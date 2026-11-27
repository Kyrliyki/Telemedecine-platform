package com.telemed.common.messaging.publisher;

import com.telemed.common.events.EventPublisher;
import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.messaging.header.MessageHeaders;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@RequiredArgsConstructor
public class RabbitEventPublisher implements EventPublisher {

  private final RabbitTemplate rabbitTemplate;
  private final String exchange;
  private final Duration confirmTimeout;

  @Override
  public void publish(IntegrationEventEnvelope event) {
    Message message =
        MessageBuilder.withBody(event.payloadJson().getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setMessageId(event.eventId().toString())
            .setHeader(MessageHeaders.CORRELATION_ID, event.correlationId())
            .setHeader(MessageHeaders.EVENT_ID, event.eventId().toString())
            .setHeader(MessageHeaders.EVENT_TYPE, event.eventType())
            .setHeader(MessageHeaders.EVENT_VERSION, event.version())
            .build();

    CorrelationData correlationData = new CorrelationData(event.eventId().toString());

    rabbitTemplate.send(exchange, event.eventType(), message, correlationData);

    awaitConfirmation(correlationData, event);
  }

  private void awaitConfirmation(CorrelationData correlationData, IntegrationEventEnvelope event) {
    try {
      CorrelationData.Confirm confirm =
          correlationData.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);

      if (!confirm.isAck()) {
        throw new IllegalStateException(
            "RabbitMQ NACK for event " + event.eventId() + ": " + confirm.getReason());
      }

      var returned = correlationData.getReturned();

      if (returned != null) {
        throw new IllegalStateException(
            "RabbitMQ returned event "
                + event.eventId()
                + ": exchange="
                + returned.getExchange()
                + ", routingKey="
                + returned.getRoutingKey()
                + ", replyCode="
                + returned.getReplyCode()
                + ", replyText="
                + returned.getReplyText());
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while waiting for RabbitMQ confirmation: " + event.eventId(), e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IllegalStateException(
          "Failed to confirm RabbitMQ publication: " + event.eventId(), e);
    }
  }
}
