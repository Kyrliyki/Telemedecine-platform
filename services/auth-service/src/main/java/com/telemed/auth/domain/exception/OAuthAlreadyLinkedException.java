package com.telemed.auth.domain.exception;

public final class OAuthAlreadyLinkedException extends DomainException {

  public OAuthAlreadyLinkedException(String provider) {
    super("OAUTH_ALREADY_LINKED", "This account is already linked to " + provider);
  }
}
