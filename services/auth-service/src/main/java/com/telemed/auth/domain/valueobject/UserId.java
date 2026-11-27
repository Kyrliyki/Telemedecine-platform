package com.telemed.auth.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record UserId(UUID value) {

  public UserId {
    Objects.requireNonNull(value, "UserId cannot be null");
  }

  public static UserId newId() {

    return new UserId(UUID.randomUUID());
  }

  public static UserId of(String raw) {
    return new UserId(UUID.fromString(raw));
  }
}
