package com.telemed.auth.application.dto.command;

import java.util.Objects;
import java.util.UUID;

public record AssignRoleCommand(UUID actorUserId, UUID targetUserId, String role) {
  public AssignRoleCommand {
    Objects.requireNonNull(actorUserId);
    Objects.requireNonNull(targetUserId);
    Objects.requireNonNull(role);
  }
}
