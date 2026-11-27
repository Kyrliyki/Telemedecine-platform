package com.telemed.user.application.exception;

public class EmailAlreadyInUseException extends RuntimeException {

  public EmailAlreadyInUseException(String email) {
    super("Email is already used by another profile: " + email);
  }
}
