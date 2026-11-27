package com.telemed.auth.domain.exception;

import com.telemed.auth.domain.valueobject.AuthStatus;

public final class InvalidStatusTransitionException extends DomainException {

  public InvalidStatusTransitionException(AuthStatus from, AuthStatus to) {
    super("INVALID_STATUS_TRANSITION", "Cannot transition status from " + from + " to " + to);
  }
}
