package com.telemed.auth.application.policy;

import java.time.Duration;

public record InvitationPolicy(Duration ttl) {

  public InvitationPolicy {
    if (ttl == null) {
      throw new IllegalArgumentException("invitation ttl must not be null");
    }
    if (ttl.isNegative() || ttl.isZero()) {
      throw new IllegalArgumentException("invitation ttl must be > 0, got " + ttl);
    }
    if (ttl.compareTo(Duration.ofDays(30)) > 0) {
      throw new IllegalArgumentException("invitation ttl must be <= 30d, got " + ttl);
    }
  }
}
