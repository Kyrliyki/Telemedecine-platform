package com.telemed.auth.domain.exception;

public final class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException() {
    super("INVALID_CREDENTIALS", "Invalid email or password");
  }
}
