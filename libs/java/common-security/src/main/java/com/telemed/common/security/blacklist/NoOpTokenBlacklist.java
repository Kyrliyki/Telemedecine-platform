package com.telemed.common.security.blacklist;

import java.time.Instant;

public final class NoOpTokenBlacklist implements TokenBlacklist {
  @Override
  public void add(String jti, Instant expiresAt) {}

  @Override
  public boolean isBlacklisted(String jti) {
    return false;
  }
}
