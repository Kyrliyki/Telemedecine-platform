package com.telemed.auth.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record UserIdentityId(UUID id) {
  public UserIdentityId {
    Objects.requireNonNull(id, "UserIdentityId.id must not be null");
  }

  public static UserIdentityId newId() {
    return new UserIdentityId(UUID.randomUUID());
  }
}
