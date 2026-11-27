package com.telemed.auth.domain.valueobject;

import java.util.Objects;

public record PasswordHash(String passwordHash) {
  public PasswordHash {
    Objects.requireNonNull(passwordHash);

    if (passwordHash.isBlank()) {
      throw new IllegalArgumentException("Password cannot be blank");
    }
  }
}
