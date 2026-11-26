package com.telemed.gateway.infrastructure.dlq;

import java.time.Instant;
import java.util.List;

public final class DlqViews {

  private DlqViews() {}

  public record DlqOverview(Instant generatedAt, List<DlqServiceView> services) {}

  public record DlqServiceView(
      String service, boolean available, String error, List<DlqQueueView> queues) {}

  public record DlqQueueView(
      String key,
      String queue,
      String exchange,
      String description,
      long messages,
      boolean available) {}

  public record DlqReplayResponse(
      String service, String queue, int replayed, int failed, boolean dryRun, long candidates) {}

  public record DlqPurgeResponse(String service, String queue, long purged) {}

  public record ServiceDlqReplayResult(
      String queue, int replayed, int failed, boolean dryRun, long candidates) {}

  public record ServiceDlqPurgeResult(String queue, long purged) {}
}
