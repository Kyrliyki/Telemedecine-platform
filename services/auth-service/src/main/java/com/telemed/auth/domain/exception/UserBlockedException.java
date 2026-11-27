package com.telemed.auth.domain.exception;

import com.telemed.auth.domain.valueobject.UserId;

public final class UserBlockedException extends DomainException {

  public UserBlockedException(UserId userId, String reason) {
    super("USER_BLOCKED", "User " + userId + " is blocked: " + reason);
  }
}
