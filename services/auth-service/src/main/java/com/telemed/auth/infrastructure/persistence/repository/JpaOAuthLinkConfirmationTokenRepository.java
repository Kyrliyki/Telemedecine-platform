package com.telemed.auth.infrastructure.persistence.repository;

import com.telemed.auth.infrastructure.persistence.entity.OAuthLinkConfirmationTokenEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaOAuthLinkConfirmationTokenRepository
    extends JpaRepository<OAuthLinkConfirmationTokenEntity, UUID> {

  Optional<OAuthLinkConfirmationTokenEntity> findByTokenHashAndUsedFalseAndExpiresAtAfter(
      String tokenHash, Instant now);

  @Modifying
  @Query(
      "UPDATE OAuthLinkConfirmationTokenEntity o SET o.used = true, o.usedAt = :now WHERE o.id = :id")
  void markUsed(@Param("id") UUID id, @Param("now") Instant now);
}
