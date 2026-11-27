package com.telemed.auth.domain.valueobject;

import java.util.Objects;

public record TokenHash(String value) {
  public TokenHash {
    Objects.requireNonNull(value, "TokenHash.value must not be null");
    if (value.isBlank()) throw new IllegalArgumentException("TokenHash must not be blank");
  }

  @Override
  public String toString() {
    return "TokenHash[***]";
  }
}
