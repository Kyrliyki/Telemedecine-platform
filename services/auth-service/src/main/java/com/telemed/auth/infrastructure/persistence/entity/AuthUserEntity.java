package com.telemed.auth.infrastructure.persistence.entity;

import com.telemed.auth.domain.valueobject.AuthStatus;
import com.telemed.auth.domain.valueobject.UserRole;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "auth_users")
public class AuthUserEntity {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AuthStatus status;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "auth_user_roles", joinColumns = @JoinColumn(name = "user_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 32)
  private Set<UserRole> roles = new HashSet<>();

  @OneToMany(
      mappedBy = "user",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private Set<UserIdentityEntity> identities = new HashSet<>();

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;
}
