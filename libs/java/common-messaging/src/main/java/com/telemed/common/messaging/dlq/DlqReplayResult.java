package com.telemed.common.messaging.dlq;

public record DlqReplayResult(
    String queue, int replayed, int failed, boolean dryRun, long candidates) {}
