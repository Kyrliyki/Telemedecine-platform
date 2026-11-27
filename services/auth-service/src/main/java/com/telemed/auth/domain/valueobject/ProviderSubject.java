package com.telemed.auth.domain.valueobject;

import java.util.Objects;

public record ProviderSubject(String value) {
  public ProviderSubject {
    Objects.requireNonNull(value, "provider subject is required");
    if (value.isBlank()) throw new IllegalArgumentException("Provider subject cannot be blank");
  }

  public static ProviderSubject fromLocal(UserId id) {
    return new ProviderSubject(id.value().toString());
  }

  public static ProviderSubject fromExternal(String sub) {
    return new ProviderSubject(sub);
  }
}
