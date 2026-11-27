package com.telemed.auth.application.policy;

import java.time.Duration;

public record RefreshTokenPolicy(Duration refreshTtl, Duration accessTtl) {
  public RefreshTokenPolicy {
    if (refreshTtl.isNegative() || refreshTtl.isZero())
      throw new IllegalArgumentException("refreshTTL > 0");
    if (accessTtl.isNegative() || accessTtl.isZero())
      throw new IllegalArgumentException("accessTTL > 0");
  }
}
