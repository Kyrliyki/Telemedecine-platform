package com.telemed.common.messaging.dlq;

public record DlqQueueView(
    String key,
    String queue,
    String exchange,
    String description,
    long messages,
    boolean available) {}
