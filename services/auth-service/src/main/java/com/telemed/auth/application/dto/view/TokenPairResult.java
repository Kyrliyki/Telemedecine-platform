package com.telemed.auth.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record TokenPairResult(
    String accessToken, String refreshToken, Instant accessExpiresAt, Instant refreshExpiresAt) {
  public static TokenPairResult mustChangePassword(UUID userId) throws Exception {
    return new TokenPairResult(null, null, null, null);
  }
}
