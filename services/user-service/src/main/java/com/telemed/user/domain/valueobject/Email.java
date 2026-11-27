package com.telemed.user.domain.valueobject;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

  public Email {
    Objects.requireNonNull(value, "Email is required");
    value = value.trim().toLowerCase(java.util.Locale.ROOT);
    if (value.isBlank()) {
      throw new IllegalArgumentException("Email must not be empty");
    }
    if (!EMAIL_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Incorrect email: " + value);
    }
  }
}
