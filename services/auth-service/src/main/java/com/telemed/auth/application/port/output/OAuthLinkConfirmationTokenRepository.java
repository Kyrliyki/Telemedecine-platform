package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OAuthLinkConfirmationTokenRepository {

  Stored save(Stored token);

  Optional<Stored> findUsable(String rawToken, Instant now);

  void markUsed(UUID id, Instant now);

  record Stored(
      UUID id,
      UserId userId,
      ProviderType provider,
      String tokenHash,
      Instant expiresAt,
      boolean used,
      String providerSubject,
      String providerEmail,
      boolean providerEmailVerified) {
    public Stored {
      if (id == null) throw new IllegalArgumentException("id required");
      if (userId == null) throw new IllegalArgumentException("userId required");
      if (provider == null) throw new IllegalArgumentException("provider required");
      if (tokenHash == null || tokenHash.isBlank()) {
        throw new IllegalArgumentException("tokenHash required");
      }
      if (expiresAt == null) throw new IllegalArgumentException("expiresAt required");
    }
  }
}
