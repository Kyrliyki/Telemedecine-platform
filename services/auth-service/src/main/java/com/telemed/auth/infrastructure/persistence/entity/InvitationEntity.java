package com.telemed.auth.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
    name = "invitations",
    indexes = {
      @Index(name = "idx_invitation_hash", columnList = "token_hash", unique = true),
      @Index(name = "idx_invitation_user", columnList = "user_id")
    })
public class InvitationEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean accepted;

  @Column(name = "accepted_at")
  private Instant acceptedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
