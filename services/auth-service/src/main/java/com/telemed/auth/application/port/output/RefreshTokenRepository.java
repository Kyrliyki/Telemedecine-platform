package com.telemed.auth.application.port.output;

import com.telemed.auth.domain.model.RefreshToken;
import com.telemed.auth.domain.valueobject.RefreshTokenId;
import com.telemed.auth.domain.valueobject.TokenHash;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
  RefreshToken save(RefreshToken token);

  Optional<RefreshToken> findByTokenHash(TokenHash tokenHash);

  Optional<RefreshToken> findById(RefreshTokenId id);

  void revokeFamily(UUID familyId);

  int revokeAllByUserId(UUID userId, Instant now);
}
