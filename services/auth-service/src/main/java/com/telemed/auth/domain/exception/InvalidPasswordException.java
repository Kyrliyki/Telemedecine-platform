package com.telemed.auth.domain.exception;

import java.util.List;

public final class InvalidPasswordException extends DomainException {

  private final List<String> violations;

  public InvalidPasswordException(List<String> violations) {
    super("INVALID_PASSWORD", "Password does not satisfy policy: " + violations);
    if (violations == null || violations.isEmpty()) {
      throw new IllegalArgumentException("InvalidPasswordException requires non-empty violations");
    }
    this.violations = List.copyOf(violations);
  }

  public List<String> violations() {
    return violations;
  }
}
