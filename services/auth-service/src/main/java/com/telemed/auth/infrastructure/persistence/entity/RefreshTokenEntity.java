package com.telemed.auth.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
    name = "refresh_tokens",
    indexes = {
      @Index(name = "idx_refresh_token_hash", columnList = "token_hash", unique = true),
      @Index(name = "idx_refresh_token_family", columnList = "family_id"),
      @Index(name = "idx_refresh_token_user", columnList = "user_id")
    })
public class RefreshTokenEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "family_id", nullable = false)
  private UUID familyId;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean revoked;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
