package com.telemed.auth.application.dto.command;

import java.util.Objects;
import java.util.UUID;

public record BlockUserCommand(UUID actorUserId, UUID targetUserId, String reason) {
  public BlockUserCommand {
    Objects.requireNonNull(actorUserId);
    Objects.requireNonNull(targetUserId);
  }
}
