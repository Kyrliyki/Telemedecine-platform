package com.telemed.auth.domain.exception;

public final class OAuthAccountConflictException extends DomainException {

  private final String provider;

  public OAuthAccountConflictException(String provider) {
    super(
        "OAUTH_ACCOUNT_CONFLICT",
        "Account with this email exists and cannot be auto-linked to "
            + provider
            + ". Please confirm your email first.");
    this.provider = provider;
  }

  public String provider() {
    return provider;
  }
}
