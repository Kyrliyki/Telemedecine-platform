package com.telemed.auth.domain.valueobject;

import java.time.Instant;
import java.util.Objects;

public record LocalCredentials(PasswordHash passwordHash, Instant updatedAt) {
  public LocalCredentials {
    Objects.requireNonNull(updatedAt);
  }

  public static LocalCredentials of(PasswordHash hash, Instant now) {
    Objects.requireNonNull(now);
    return new LocalCredentials(hash, now);
  }
}
