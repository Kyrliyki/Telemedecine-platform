package com.telemed.auth.infrastructure.persistence.entity;

import com.telemed.auth.domain.valueobject.ProviderType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Setter
@Getter
@Table(
    name = "user_identities",
    uniqueConstraints = {
      @UniqueConstraint(columnNames = {"provider", "provider_subject"}),
      @UniqueConstraint(columnNames = {"user_id", "provider"})
    })
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "identity_type")
public abstract class UserIdentityEntity {

  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private AuthUserEntity user;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ProviderType provider;

  @Column(name = "provider_subject", nullable = false)
  private String providerSubject;

  @Column(name = "linked_at", nullable = false)
  private Instant linkedAt;
}
