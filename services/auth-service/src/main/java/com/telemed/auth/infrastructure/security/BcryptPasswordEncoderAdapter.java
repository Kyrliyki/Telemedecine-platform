package com.telemed.auth.infrastructure.security;

import com.telemed.auth.application.port.output.PasswordEncoder;
import com.telemed.auth.domain.valueobject.PasswordHash;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptPasswordEncoderAdapter implements PasswordEncoder {

  private final BCryptPasswordEncoder delegate;

  public BcryptPasswordEncoderAdapter(BCryptPasswordEncoder delegate) {
    this.delegate = delegate;
  }

  @Override
  public PasswordHash hash(String rawPassword) {
    return new PasswordHash(delegate.encode(rawPassword));
  }

  @Override
  public boolean matches(String rawPassword, PasswordHash hash) {
    return delegate.matches(rawPassword, hash.passwordHash());
  }
}
