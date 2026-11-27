package com.telemed.auth.infrastructure.security;

import com.telemed.auth.application.port.output.PasswordPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class DefaultPasswordPolicy implements PasswordPolicy {

  private static final int MIN_LENGTH = 12;
  private static final int MAX_LENGTH = 128;

  @Override
  public List<String> validate(String rawPassword, String email) {
    List<String> violations = new ArrayList<>();
    if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
      violations.add("password must be at least " + MIN_LENGTH + " characters");
    }
    if (rawPassword != null && rawPassword.length() > MAX_LENGTH) {
      violations.add("password must be at most " + MAX_LENGTH + " characters");
    }
    if (rawPassword == null) {
      return violations;
    }
    if (rawPassword.chars().noneMatch(Character::isUpperCase)) {
      violations.add("password must contain an uppercase letter");
    }
    if (rawPassword.chars().noneMatch(Character::isLowerCase)) {
      violations.add("password must contain a lowercase letter");
    }
    if (rawPassword.chars().noneMatch(Character::isDigit)) {
      violations.add("password must contain a digit");
    }
    if (rawPassword.chars().noneMatch(c -> !Character.isLetterOrDigit(c))) {
      violations.add("password must contain a special character");
    }
    if (email != null) {
      String localPart = email.toLowerCase(Locale.ROOT).split("@", 2)[0];
      if (localPart.length() >= 3 && rawPassword.toLowerCase(Locale.ROOT).contains(localPart)) {
        violations.add("password must not contain the email local part");
      }
    }
    return violations;
  }
}
