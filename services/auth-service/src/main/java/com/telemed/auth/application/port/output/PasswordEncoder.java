package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.PasswordHash;

public interface PasswordEncoder {
  PasswordHash hash(String rawPassword);

  boolean matches(String rawPassword, PasswordHash encodedPassword);
}
