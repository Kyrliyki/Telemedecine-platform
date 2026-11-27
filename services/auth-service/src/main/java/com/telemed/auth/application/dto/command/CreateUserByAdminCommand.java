package com.telemed.auth.application.dto.command;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record CreateUserByAdminCommand(String email, Set<String> roles, UUID actorUserId) {
  public CreateUserByAdminCommand {
    Objects.requireNonNull(email);
    Objects.requireNonNull(roles);
    if (roles.isEmpty()) throw new IllegalArgumentException("roles required");
  }
}
