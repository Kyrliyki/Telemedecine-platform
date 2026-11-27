package com.telemed.auth.domain.exception;

public final class InvitationExpiredException extends DomainException {

  public InvitationExpiredException() {
    super("INVITATION_EXPIRED", "Invitation has expired");
  }
}
