package com.telemed.auth.domain.valueobject;

public enum AuthStatus {
  BLOCKED,
  ACTIVE,
  PENDING_INVITATION,
  PENDING_EMAIL_VERIFICATION;

  public boolean canAuthenticate() {
    return this == ACTIVE;
  }
}
