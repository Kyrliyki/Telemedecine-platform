package com.telemed.auth.domain.valueobject;

import java.util.Objects;
import java.util.regex.Pattern;

public record Email(String value) {
  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

  public Email {
    Objects.requireNonNull(value, "Email is required");
    if (value.isBlank()) {
      throw new IllegalArgumentException("Email not be empty");
    }
    value = value.toLowerCase().trim();
    if (!EMAIL_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException("Incorrect Email: " + value);
    }
  }
}
