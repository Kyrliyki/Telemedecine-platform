package com.telemed.common.outbox.entity;

public enum OutboxStatus {
  PENDING,
  PROCESSING,
  SENT,
  FAILED,
  DEAD
}
