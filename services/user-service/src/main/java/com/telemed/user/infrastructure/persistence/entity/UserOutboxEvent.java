package com.telemed.user.infrastructure.persistence.entity;

import com.telemed.common.outbox.entity.OutboxEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "outbox_event",
    indexes = {
      @Index(
          name = "idx_outbox_pending",
          columnList = "service, status, next_attempt_at, created_at"),
      @Index(name = "idx_outbox_event_id", columnList = "event_id", unique = true)
    })
public class UserOutboxEvent extends OutboxEvent {}
