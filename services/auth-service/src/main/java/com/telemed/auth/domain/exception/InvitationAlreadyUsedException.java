package com.telemed.auth.domain.exception;

public final class InvitationAlreadyUsedException extends DomainException {

  public InvitationAlreadyUsedException() {
    super("INVITATION_ALREADY_USED", "Invitation has already been used");
  }
}
