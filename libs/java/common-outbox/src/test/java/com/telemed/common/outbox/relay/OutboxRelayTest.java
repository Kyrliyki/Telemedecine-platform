package com.telemed.common.outbox.relay;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.common.events.EventPublisher;
import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.outbox.config.OutboxProperties;
import com.telemed.common.outbox.entity.OutboxEvent;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.metrics.OutboxMetrics;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

  private static final String SERVICE = "test-service";
  private static final int MAX_ATTEMPTS = 2;

  @Mock OutboxPersistenceService persistence;
  @Mock EventPublisher publisher;
  @Mock OutboxMetrics metrics;
  @Mock OutboxEventFactory eventFactory;

  private OutboxRelay relay;
  private TestOutboxEvent event;
  private IntegrationEventEnvelope envelope;

  @BeforeEach
  void setUp() {
    OutboxProperties properties =
        new OutboxProperties(
            10,
            Duration.ofDays(1),
            MAX_ATTEMPTS,
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            Duration.ofMinutes(5));
    relay = new OutboxRelay(persistence, publisher, properties, metrics, eventFactory, SERVICE);

    event = new TestOutboxEvent();
    event.setEventId(UUID.randomUUID());
    event.setEventType("test.event.v1");
    event.setService(SERVICE);
    event.setRetryCount(0);

    envelope =
        new IntegrationEventEnvelope(
            event.getEventId(), event.getEventType(), Instant.now(), "correlation-1", "{}", 1);
  }

  @Test
  void publishesAndMarksSent() {
    when(persistence.markDeadStuck(eq(SERVICE), any(), eq(MAX_ATTEMPTS))).thenReturn(0);
    when(persistence.claimPending(eq(SERVICE), anyInt(), eq(MAX_ATTEMPTS), any(), any()))
        .thenReturn(List.of(event));
    when(eventFactory.toEnvelope(event)).thenReturn(envelope);

    relay.relay();

    verify(publisher).publish(envelope);
    verify(persistence).markSent(event.getEventId());
    verify(persistence, never()).markFailed(any(), any(), any(), eq(true));
    verify(metrics).recordSent(1);
  }

  @Test
  void schedulesRetryOnFailure() {
    event.setRetryCount(0);
    when(persistence.markDeadStuck(eq(SERVICE), any(), eq(MAX_ATTEMPTS))).thenReturn(0);
    when(persistence.claimPending(eq(SERVICE), anyInt(), eq(MAX_ATTEMPTS), any(), any()))
        .thenReturn(List.of(event));
    when(eventFactory.toEnvelope(event)).thenReturn(envelope);
    doThrow(new RuntimeException("broker unavailable")).when(publisher).publish(envelope);

    relay.relay();

    verify(persistence)
        .markFailed(eq(event.getEventId()), contains("broker unavailable"), any(), eq(false));
    verify(metrics).recordFailed(1);
  }

  @Test
  void marksEventDeadAfterMaxAttempts() {
    event.setRetryCount(MAX_ATTEMPTS - 1);
    when(persistence.markDeadStuck(eq(SERVICE), any(), eq(MAX_ATTEMPTS))).thenReturn(0);
    when(persistence.claimPending(eq(SERVICE), anyInt(), eq(MAX_ATTEMPTS), any(), any()))
        .thenReturn(List.of(event));
    when(eventFactory.toEnvelope(event)).thenReturn(envelope);
    doThrow(new RuntimeException("permanent failure")).when(publisher).publish(envelope);

    relay.relay();

    verify(persistence)
        .markFailed(eq(event.getEventId()), contains("permanent failure"), isNull(), eq(true));
    verify(metrics).recordDead(1);
  }

  @Test
  void recordsRecoveredStuckEvents() {
    when(persistence.markDeadStuck(eq(SERVICE), any(), eq(MAX_ATTEMPTS))).thenReturn(2);
    when(persistence.claimPending(eq(SERVICE), anyInt(), eq(MAX_ATTEMPTS), any(), any()))
        .thenReturn(List.of());

    relay.relay();

    verify(metrics).recordDead(2);
    verify(metrics).recordPending(0);
  }

  private static final class TestOutboxEvent extends OutboxEvent {}
}
