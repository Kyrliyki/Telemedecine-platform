package com.telemed.auth.application.dto.command;

import java.util.Objects;

public record AcceptInvitationCommand(String rawInviteToken, String rawPassword) {
  public AcceptInvitationCommand {
    Objects.requireNonNull(rawInviteToken);
    Objects.requireNonNull(rawPassword);
  }
}
