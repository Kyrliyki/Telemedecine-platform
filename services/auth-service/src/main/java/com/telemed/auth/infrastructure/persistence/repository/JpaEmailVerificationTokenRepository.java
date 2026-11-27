package com.telemed.auth.infrastructure.persistence.repository;

import com.telemed.auth.infrastructure.persistence.entity.EmailVerificationTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaEmailVerificationTokenRepository
    extends JpaRepository<EmailVerificationTokenEntity, UUID> {

  Optional<EmailVerificationTokenEntity> findByTokenHashAndUsedFalseAndExpiresAtAfter(
      String tokenHash, Instant now);

  @Modifying
  @Query(
      """
            UPDATE EmailVerificationTokenEntity t
            SET t.used = true, t.usedAt = :now
            WHERE t.id = :id AND t.used = false
            """)
  void markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
