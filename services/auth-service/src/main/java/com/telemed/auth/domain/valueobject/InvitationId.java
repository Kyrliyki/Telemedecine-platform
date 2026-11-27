package com.telemed.auth.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record InvitationId(UUID value) {

  public InvitationId {
    Objects.requireNonNull(value, "InvitationId cannot be null");
  }

  public static InvitationId newId() {

    return new InvitationId(UUID.randomUUID());
  }

  public static InvitationId of(String raw) {
    return new InvitationId(UUID.fromString(raw));
  }
}
