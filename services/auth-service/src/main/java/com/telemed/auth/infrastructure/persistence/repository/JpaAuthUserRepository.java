package com.telemed.auth.infrastructure.persistence.repository;

import com.telemed.auth.domain.valueobject.ProviderType;
import com.telemed.auth.infrastructure.persistence.entity.AuthUserEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JpaAuthUserRepository
    extends JpaRepository<AuthUserEntity, UUID>, JpaSpecificationExecutor<AuthUserEntity> {
  Optional<AuthUserEntity> findByEmail(String email);

  boolean existsByEmail(String email);

  @Query(
      """
            SELECT DISTINCT u FROM AuthUserEntity u
            JOIN u.identities i
            WHERE i.provider = :provider AND i.providerSubject = :subject
            """)
  Optional<AuthUserEntity> findByExternalSubject(
      @Param("provider") ProviderType provider, @Param("subject") String subject);
}
