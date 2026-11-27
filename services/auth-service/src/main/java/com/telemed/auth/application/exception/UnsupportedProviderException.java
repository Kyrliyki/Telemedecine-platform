package com.telemed.auth.application.exception;

public class UnsupportedProviderException extends RuntimeException {
  public UnsupportedProviderException(String provider) {
    super("Unsupported provider: " + provider);
  }
}
