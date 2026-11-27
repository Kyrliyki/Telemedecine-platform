package com.telemed.auth.application.dto.command;

import java.util.Objects;

public record LoginLocalCommand(String email, String rawPassword) {
  public LoginLocalCommand {
    Objects.requireNonNull(email);
    Objects.requireNonNull(rawPassword);
  }
}
