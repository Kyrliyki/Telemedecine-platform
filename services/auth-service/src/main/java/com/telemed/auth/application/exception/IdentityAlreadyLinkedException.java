package com.telemed.auth.application.exception;

public class IdentityAlreadyLinkedException extends RuntimeException {
  public IdentityAlreadyLinkedException(String provider, String providerId) {
    super("Identity already linked: " + provider + "/" + providerId);
  }
}
