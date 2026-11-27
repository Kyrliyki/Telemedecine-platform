package com.telemed.auth.domain.model;

import com.telemed.auth.domain.valueobject.InvitationId;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class Invitation {
  private final InvitationId id;
  private final UserId userId;
  private final TokenHash tokenHash;
  private final Instant createdAt;
  private final Instant expiresAt;
  private boolean accepted;
  private Instant acceptedAt;

  private Invitation(
      InvitationId id,
      UserId userId,
      TokenHash tokenHash,
      Instant createdAt,
      Instant expiresAt,
      boolean accepted,
      Instant acceptedAt) {
    this.id = Objects.requireNonNull(id);
    this.userId = Objects.requireNonNull(userId);
    this.tokenHash = Objects.requireNonNull(tokenHash);
    this.createdAt = Objects.requireNonNull(createdAt);
    this.expiresAt = Objects.requireNonNull(expiresAt);
    this.accepted = accepted;
    this.acceptedAt = acceptedAt;
  }

  public static Invitation create(UserId userId, TokenHash tokenHash, Instant now, Duration ttl) {
    return new Invitation(InvitationId.newId(), userId, tokenHash, now, now.plus(ttl), false, null);
  }

  public static Invitation rehydrate(
      InvitationId id,
      UserId userId,
      TokenHash tokenHash,
      Instant createdAt,
      Instant expiresAt,
      boolean accepted,
      Instant acceptedAt) {
    return new Invitation(id, userId, tokenHash, createdAt, expiresAt, accepted, acceptedAt);
  }

  public void accept(Instant now) {
    if (accepted) throw new IllegalStateException("Invitation already accepted");
    if (now.isAfter(expiresAt)) throw new IllegalStateException("Invitation expired");
    accepted = true;
    acceptedAt = now;
  }

  public boolean isUsable(Instant now) {
    return !accepted && now.isBefore(expiresAt);
  }

  public InvitationId id() {
    return id;
  }

  public UserId userId() {
    return userId;
  }

  public TokenHash tokenHash() {
    return tokenHash;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public boolean accepted() {
    return accepted;
  }

  public Instant acceptedAt() {
    return acceptedAt;
  }
}
