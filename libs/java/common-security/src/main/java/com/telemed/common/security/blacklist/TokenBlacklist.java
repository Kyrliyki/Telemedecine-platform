package com.telemed.common.security.blacklist;

import java.time.Instant;

public interface TokenBlacklist {

  void add(String jti, Instant expiresAt);

  boolean isBlacklisted(String jti);
}
