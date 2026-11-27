package com.telemed.auth.application.dto.command;

import java.util.Objects;

public record RefreshTokenCommand(String rawRefreshToken) {
  public RefreshTokenCommand {
    Objects.requireNonNull(rawRefreshToken);
  }
}
