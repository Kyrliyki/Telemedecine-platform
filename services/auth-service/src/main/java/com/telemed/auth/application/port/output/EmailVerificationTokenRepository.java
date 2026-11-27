package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepository {

  Stored save(Stored token);

  Optional<Stored> findUsable(String rawToken, Instant now);

  void markUsed(UUID id, Instant now);

  record Stored(UUID id, UserId userId, String tokenHash, Instant expiresAt, boolean used) {}
}
