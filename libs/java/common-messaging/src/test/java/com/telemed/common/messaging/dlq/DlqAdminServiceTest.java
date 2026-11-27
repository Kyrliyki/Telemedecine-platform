package com.telemed.common.messaging.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.common.messaging.header.MessageHeaders;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class DlqAdminServiceTest {

  private static final DlqQueueDefinition AUTH =
      new DlqQueueDefinition("auth", "service.auth.events.dlq", "auth.events", "auth events");

  @Mock private RabbitTemplate rabbitTemplate;

  private DlqAdminService service;

  @BeforeEach
  void setUp() {
    service = new DlqAdminService(rabbitTemplate, new DlqQueueCatalog(List.of(AUTH)));
  }

  @Test
  void replayPublishesMessageBackToSourceExchangeAndClearsDeathHeaders() {
    Message message = messageWithEvent("auth.user.registered.v1");
    message
        .getMessageProperties()
        .getHeaders()
        .put("x-death", List.of(Map.of("reason", "rejected")));
    when(rabbitTemplate.execute(any())).thenReturn(1L);
    when(rabbitTemplate.receive(anyString())).thenReturn(message, (Message) null);
    doAnswer(
            invocation -> {
              CorrelationData correlation = invocation.getArgument(3);
              correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
              return null;
            })
        .when(rabbitTemplate)
        .send(
            eq(AUTH.exchange()),
            eq("auth.user.registered.v1"),
            eq(message),
            any(CorrelationData.class));

    DlqReplayResult result = service.replay("auth", 10, false);

    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    assertThat(result.dryRun()).isFalse();
    assertThat(message.getMessageProperties().getHeaders()).doesNotContainKey("x-death");
    verify(rabbitTemplate)
        .send(
            eq(AUTH.exchange()),
            eq("auth.user.registered.v1"),
            eq(message),
            any(CorrelationData.class));
  }

  @Test
  void dryRunDoesNotConsumeMessages() {
    DlqReplayResult result = service.replay("auth", 10, true);

    assertThat(result.dryRun()).isTrue();
    assertThat(result.replayed()).isZero();
    assertThat(result.candidates()).isGreaterThanOrEqualTo(0);
    verify(rabbitTemplate, never()).receive(any(String.class));
  }

  @Test
  void rejectsUnknownQueueKey() {
    assertThatThrownBy(() -> service.replay("unknown", 10, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown DLQ");
  }

  @Test
  void requeuesMessageWithoutRoutingKey() {
    Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());
    when(rabbitTemplate.execute(any())).thenReturn(1L);
    when(rabbitTemplate.receive(anyString())).thenReturn(message);

    DlqReplayResult result = service.replay("auth", 10, false);

    assertThat(result.failed()).isEqualTo(1);
    verify(rabbitTemplate).send("", AUTH.queueName(), message);
  }

  @Test
  void listReturnsQueueMetadata() {
    when(rabbitTemplate.execute(any())).thenReturn(7L);

    List<DlqQueueView> result = service.list();

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().key()).isEqualTo("auth");
    assertThat(result.getFirst().messages()).isEqualTo(7);
    assertThat(result.getFirst().available()).isTrue();
  }

  @Test
  void purgeReturnsPurgedMessageCount() {
    when(rabbitTemplate.execute(any())).thenReturn(4);

    DlqPurgeResult result = service.purge("auth");

    assertThat(result.queue()).isEqualTo("auth");
    assertThat(result.purged()).isEqualTo(4);
  }

  private static Message messageWithEvent(String eventType) {
    MessageProperties properties = new MessageProperties();
    properties.setHeader(MessageHeaders.EVENT_TYPE, eventType);
    return new Message("{}".getBytes(StandardCharsets.UTF_8), properties);
  }
}
