package com.telemed.auth.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record RefreshTokenId(UUID value) {

  public RefreshTokenId {
    Objects.requireNonNull(value, "RefreshTokenId cannot be null");
  }

  public static RefreshTokenId newId() {

    return new RefreshTokenId(UUID.randomUUID());
  }

  public static RefreshTokenId of(String raw) {
    return new RefreshTokenId(UUID.fromString(raw));
  }
}
