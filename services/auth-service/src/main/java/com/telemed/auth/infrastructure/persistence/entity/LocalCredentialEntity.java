package com.telemed.auth.infrastructure.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "local_credentials")
public class LocalCredentialEntity {

  @Id
  @Column(name = "identity_id")
  private UUID identityId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "identity_id")
  private LocalIdentityEntity identity;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;
}
