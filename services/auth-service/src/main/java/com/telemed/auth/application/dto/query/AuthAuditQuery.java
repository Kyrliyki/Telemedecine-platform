package com.telemed.auth.application.dto.query;

import java.time.Instant;
import java.util.UUID;

public record AuthAuditQuery(
    UUID targetUserId, UUID actorId, String action, Instant from, Instant to, int page, int size) {

  public AuthAuditQuery {
    page = Math.max(page, 0);
    size = size <= 0 ? 20 : Math.min(size, 200);
  }
}
