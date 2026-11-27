package com.telemed.auth.domain.valueobject;

public enum ProviderType {
  LOCAL,
  GOOGLE;

  public boolean isExternal() {
    return this != LOCAL;
  }
}
