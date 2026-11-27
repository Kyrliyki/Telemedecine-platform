package com.telemed.common.outbox.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@MappedSuperclass
@Setter
@Getter
public abstract class OutboxEvent {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false, unique = true)
  private UUID eventId;

  @Column(name = "event_type", nullable = false)
  private String eventType;

  @Column(name = "service", nullable = false)
  private String service;

  @Column(name = "payload", nullable = false, columnDefinition = "text")
  private String payload;

  @Column(name = "correlation_id")
  private String correlationId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OutboxStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "retry_count", nullable = false)
  private int retryCount;

  @Column(name = "last_error", columnDefinition = "text")
  private String lastError;

  @Column(name = "next_attempt_at")
  private Instant nextAttemptAt;

  @Column(name = "processing_started_at")
  private Instant processingStartedAt;

  protected OutboxEvent() {}
}
