package com.telemed.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultPasswordPolicyTest {

  private final DefaultPasswordPolicy policy = new DefaultPasswordPolicy();

  @Test
  void acceptsStrongPassword() {
    List<String> violations = policy.validate("StrongPassw0rd!", "patient@example.com");

    assertThat(violations).isEmpty();
  }

  @Test
  void reportsMissingCharacterClasses() {
    List<String> violations = policy.validate("weakpass", "patient@example.com");

    assertThat(violations)
        .contains(
            "password must be at least 12 characters",
            "password must contain an uppercase letter",
            "password must contain a digit",
            "password must contain a special character");
  }

  @Test
  void rejectsPasswordContainingEmailLocalPart() {
    List<String> violations = policy.validate("Patient12345!", "patient@example.com");

    assertThat(violations).contains("password must not contain the email local part");
  }
}
