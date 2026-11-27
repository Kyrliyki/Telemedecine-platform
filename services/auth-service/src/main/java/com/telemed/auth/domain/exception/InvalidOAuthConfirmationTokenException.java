package com.telemed.auth.domain.exception;

public final class InvalidOAuthConfirmationTokenException extends DomainException {

  public InvalidOAuthConfirmationTokenException() {
    super(
        "INVALID_OAUTH_CONFIRMATION_TOKEN", "OAuth link confirmation token is invalid or expired");
  }
}
