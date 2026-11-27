package com.telemed.auth.application.dto.command;

import java.util.Objects;

public record VerifyEmailCommand(String rawToken) {
  public VerifyEmailCommand {
    Objects.requireNonNull(rawToken);
  }
}
