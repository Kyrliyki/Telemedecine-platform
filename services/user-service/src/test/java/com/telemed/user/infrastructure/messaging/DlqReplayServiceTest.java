package com.telemed.user.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.user.infrastructure.audit.UserAuditLogger;
import com.telemed.user.infrastructure.config.RabbitConfig;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class DlqReplayServiceTest {

  @Test
  void republishesMessageToAuthExchange() {
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    UserAuditLogger audit = mock(UserAuditLogger.class);
    MessageProperties properties = new MessageProperties();
    properties.setHeader(MessageHeaders.EVENT_TYPE, "auth.user.registered.v1");
    Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), properties);

    when(rabbitTemplate.receive(RabbitConfig.AUTH_DLQ)).thenReturn(message).thenReturn(null);

    DlqReplayService.ReplayResult result = new DlqReplayService(rabbitTemplate, audit).replay(10);

    assertThat(result.replayed()).isEqualTo(1);
    assertThat(result.failed()).isZero();
    verify(rabbitTemplate)
        .send(eq(RabbitConfig.AUTH_EXCHANGE), eq("auth.user.registered.v1"), eq(message));
    verify(audit).dlqReplay(1, 0);
  }

  @Test
  void returnsMessageWithoutEventTypeToDlq() {
    RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    UserAuditLogger audit = mock(UserAuditLogger.class);
    Message message = new Message("{}".getBytes(StandardCharsets.UTF_8), new MessageProperties());

    when(rabbitTemplate.receive(RabbitConfig.AUTH_DLQ)).thenReturn(message).thenReturn(null);

    DlqReplayService.ReplayResult result = new DlqReplayService(rabbitTemplate, audit).replay(10);

    assertThat(result.failed()).isEqualTo(1);
    verify(rabbitTemplate).send(eq(""), eq(RabbitConfig.AUTH_DLQ), eq(message));
    verify(audit).dlqReplay(0, 1);
  }
}
