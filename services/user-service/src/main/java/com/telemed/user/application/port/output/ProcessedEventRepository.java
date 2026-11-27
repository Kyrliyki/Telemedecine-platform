package com.telemed.user.application.port.output;

import java.time.Instant;
import java.util.UUID;

public interface ProcessedEventRepository {

  boolean tryClaim(UUID eventId, String eventType, Instant claimedAt);

  int deleteProcessedBefore(Instant cutoff);
}
