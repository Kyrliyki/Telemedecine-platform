package com.telemed.auth.application.dto.command;

import java.util.Objects;

public record RegisterLocalCommand(String email, String rawPassword) {
  public RegisterLocalCommand {
    Objects.requireNonNull(email);
    Objects.requireNonNull(rawPassword);
  }
}
