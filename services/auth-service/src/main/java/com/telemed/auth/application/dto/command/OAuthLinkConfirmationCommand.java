package com.telemed.auth.application.dto.command;

import com.telemed.auth.domain.valueobject.ProviderType;
import java.util.Objects;

public record OAuthLinkConfirmationCommand(String rawToken, ProviderType provider) {
  public OAuthLinkConfirmationCommand {
    Objects.requireNonNull(rawToken, "token required");
    Objects.requireNonNull(provider, "provider required");
  }
}
