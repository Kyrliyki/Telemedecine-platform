package com.telemed.auth.infrastructure.persistence.repository;

import com.telemed.auth.infrastructure.persistence.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaRefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
  Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT r FROM RefreshTokenEntity r WHERE r.tokenHash = :tokenHash")
  Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  @Modifying
  @Query(
      "UPDATE RefreshTokenEntity r SET r.revoked = true, r.revokedAt = :now WHERE r.familyId = :familyId")
  void revokeByFamilyId(@Param("familyId") UUID familyId, @Param("now") Instant now);

  @Modifying
  @Query(
      "UPDATE RefreshTokenEntity r SET r.revoked = true, r.revokedAt = :now WHERE r.userId = :userId AND r.revoked = false")
  int revokeAllByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
