package com.telemed.auth.application.dto.command;

import java.util.Objects;

public record LoginWithOAuthCommand(String authorizationCode, String redirectUri) {
  public LoginWithOAuthCommand {
    Objects.requireNonNull(authorizationCode);
  }
}
