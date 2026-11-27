package com.telemed.auth.domain.exception;

public final class InvalidInvitationException extends DomainException {

  public InvalidInvitationException() {
    super("INVALID_INVITATION", "Invitation token is invalid");
  }
}
