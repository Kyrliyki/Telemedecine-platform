package com.telemed.user.domain.valueobject;

import java.util.Objects;

public record UserId(java.util.UUID value) {

  public UserId {
    Objects.requireNonNull(value, "UserId cannot be null");
  }

  public static UserId newId() {
    return new UserId(java.util.UUID.randomUUID());
  }

  public static UserId of(java.util.UUID value) {
    return new UserId(value);
  }
}
