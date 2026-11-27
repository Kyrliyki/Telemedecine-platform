package com.telemed.auth.domain.exception;

public final class InvalidVerificationTokenException extends DomainException {

  public InvalidVerificationTokenException() {
    super("INVALID_VERIFICATION_TOKEN", "Email verification token is invalid or expired");
  }
}
