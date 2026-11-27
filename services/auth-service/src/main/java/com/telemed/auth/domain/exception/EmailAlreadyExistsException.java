package com.telemed.auth.domain.exception;

import com.telemed.auth.domain.valueobject.Email;

public final class EmailAlreadyExistsException extends DomainException {

  public EmailAlreadyExistsException(Email email) {
    super("EMAIL_ALREADY_EXISTS", "Email already registered: " + email.value());
  }
}
