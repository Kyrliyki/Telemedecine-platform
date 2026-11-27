package com.telemed.user.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.telemed.common.events.AuthEventTypes;
import com.telemed.common.messaging.header.MessageHeaders;
import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.ProcessedEventRepository;
import com.telemed.user.application.service.UserSyncService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AuthEventConsumerTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Mock private UserSyncService userSyncService;

  @Mock private ProcessedEventRepository processedEventRepository;

  @Mock private Clock clock;

  private AuthEventConsumer consumer;

  @BeforeEach
  void setUp() {
    consumer =
        new AuthEventConsumer(
            JsonMapper.builder().build(),
            userSyncService,
            processedEventRepository,
            new AuthEventMetrics(new SimpleMeterRegistry()),
            clock);
  }

  @Test
  void claimsAndDispatchesUserRegisteredEvent() {
    UUID eventId = UUID.randomUUID();
    when(clock.now()).thenReturn(NOW);
    when(processedEventRepository.tryClaim(eventId, AuthEventTypes.USER_REGISTERED, NOW))
        .thenReturn(true);

    consumer.handle(message(eventId, AuthEventTypes.USER_REGISTERED, registeredBody()));

    verify(userSyncService).onUserRegistered(any());
  }

  @Test
  void skipsAlreadyClaimedEvent() {
    UUID eventId = UUID.randomUUID();
    when(clock.now()).thenReturn(NOW);
    when(processedEventRepository.tryClaim(eventId, AuthEventTypes.USER_REGISTERED, NOW))
        .thenReturn(false);

    consumer.handle(message(eventId, AuthEventTypes.USER_REGISTERED, "{}"));

    verifyNoInteractions(userSyncService);
  }

  @Test
  void rethrowsFailureSoRabbitRoutesToDlq() {
    UUID eventId = UUID.randomUUID();
    when(clock.now()).thenReturn(NOW);
    when(processedEventRepository.tryClaim(eventId, AuthEventTypes.USER_REGISTERED, NOW))
        .thenReturn(true);
    doThrow(new IllegalStateException("db down")).when(userSyncService).onUserRegistered(any());

    assertThatThrownBy(
            () ->
                consumer.handle(message(eventId, AuthEventTypes.USER_REGISTERED, registeredBody())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void ignoresUnknownEventType() {
    UUID eventId = UUID.randomUUID();
    when(clock.now()).thenReturn(NOW);
    when(processedEventRepository.tryClaim(eq(eventId), eq("auth.user.unknown.v1"), eq(NOW)))
        .thenReturn(true);

    consumer.handle(message(eventId, "auth.user.unknown.v1", "{}"));

    verifyNoInteractions(userSyncService);
  }

  private static String registeredBody() {
    return """
        {
          "userId": "11111111-1111-1111-1111-111111111111",
          "email": "new@example.com",
          "roles": ["PATIENT"],
          "emailVerified": true,
          "source": "PASSWORD"
        }
        """;
  }

  private static Message message(UUID eventId, String eventType, String body) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setMessageId(eventId.toString());
    properties.setHeader(MessageHeaders.EVENT_ID, eventId.toString());
    properties.setHeader(MessageHeaders.EVENT_TYPE, eventType);
    properties.setHeader(MessageHeaders.EVENT_VERSION, 1);
    properties.setHeader(MessageHeaders.CORRELATION_ID, "corr-test");
    return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
  }
}
