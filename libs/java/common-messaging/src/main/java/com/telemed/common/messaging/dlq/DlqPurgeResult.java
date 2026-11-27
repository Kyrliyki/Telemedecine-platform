package com.telemed.common.messaging.dlq;

public record DlqPurgeResult(String queue, long purged) {}
