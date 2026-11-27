package com.telemed.auth.application.dto.query;

public record AdminUserQuery(int page, int size, String email, String status, String role) {
  public AdminUserQuery {
    if (page < 0) page = 0;
    if (size <= 0) size = 20;
    if (size > 100) size = 100;
    email = normalize(email);
    status = normalize(status);
    role = normalize(role);
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }
}
