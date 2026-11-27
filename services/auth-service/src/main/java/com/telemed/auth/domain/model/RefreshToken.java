package com.telemed.auth.domain.model;

import com.telemed.auth.domain.valueobject.RefreshTokenId;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RefreshToken {
  private final RefreshTokenId id;
  private final UserId userId;
  private final TokenHash tokenHash;
  private final UUID familyId;
  private final Instant issuedAt;
  private final Instant expiresAt;
  private boolean revoked;
  private Instant revokedAt;

  private RefreshToken(
      RefreshTokenId id,
      UserId userId,
      TokenHash tokenHash,
      UUID familyId,
      Instant issuedAt,
      Instant expiresAt,
      boolean revoked,
      Instant revokedAt) {
    this.id = Objects.requireNonNull(id);
    this.userId = Objects.requireNonNull(userId);
    this.tokenHash = Objects.requireNonNull(tokenHash);
    this.familyId = Objects.requireNonNull(familyId);
    this.issuedAt = Objects.requireNonNull(issuedAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    if (!expiresAt.isAfter(issuedAt)) {
      throw new IllegalArgumentException("expiresAt must be after issuedAt");
    }
    this.revoked = revoked;
    this.revokedAt = revokedAt;
  }

  public static RefreshToken issue(UserId userId, TokenHash tokenHash, Instant now, Duration ttl) {
    return new RefreshToken(
        RefreshTokenId.newId(),
        userId,
        tokenHash,
        UUID.randomUUID(),
        now,
        now.plus(ttl),
        false,
        null);
  }

  public RefreshToken rotate(TokenHash newHash, Instant now, Duration ttl) {
    if (!isActive(now)) throw new IllegalStateException("Cannot rotate inactive token");
    this.revoked = true;
    this.revokedAt = now;
    return new RefreshToken(
        RefreshTokenId.newId(), userId, newHash, familyId, now, now.plus(ttl), false, null);
  }

  public void revoke(Instant now) {
    if (revoked) return;
    revoked = true;
    revokedAt = now;
  }

  public static RefreshToken rehydrate(
      RefreshTokenId id,
      UserId userId,
      TokenHash tokenHash,
      UUID familyId,
      Instant issuedAt,
      Instant expiresAt,
      boolean revoked,
      Instant revokedAt) {
    return new RefreshToken(
        id, userId, tokenHash, familyId, issuedAt, expiresAt, revoked, revokedAt);
  }

  public boolean isActive(Instant now) {
    return !revoked && now.isBefore(expiresAt);
  }

  public RefreshTokenId id() {
    return id;
  }

  public UserId userId() {
    return userId;
  }

  public TokenHash tokenHash() {
    return tokenHash;
  }

  public UUID familyId() {
    return familyId;
  }

  public Instant issuedAt() {
    return issuedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public boolean revoked() {
    return revoked;
  }

  public Instant revokedAt() {
    return revokedAt;
  }
}
