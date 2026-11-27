package com.telemed.auth.infrastructure.persistence.entity;

import com.telemed.auth.domain.valueobject.ProviderType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(
    name = "oauth_link_confirmation_tokens",
    indexes = {
      @Index(name = "idx_oauth_link_hash", columnList = "token_hash", unique = true),
      @Index(name = "idx_oauth_link_user", columnList = "user_id")
    })
public class OAuthLinkConfirmationTokenEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ProviderType provider;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private boolean used;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "provider_subject", length = 255)
  private String providerSubject;

  @Column(name = "provider_email", length = 320)
  private String providerEmail;

  @Column(name = "provider_email_verified", nullable = false)
  private boolean providerEmailVerified;
}
