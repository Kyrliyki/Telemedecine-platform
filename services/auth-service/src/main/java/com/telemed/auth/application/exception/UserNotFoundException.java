package com.telemed.auth.application.exception;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(java.util.UUID id) {
    super("User not found: " + id);
  }
}
