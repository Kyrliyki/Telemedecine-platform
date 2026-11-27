package com.telemed.auth.application.dto.command;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record InviteUserCommand(String email, Set<String> roles, UUID actorUserId) {
  public InviteUserCommand {
    Objects.requireNonNull(email);
    Objects.requireNonNull(roles);
  }
}
