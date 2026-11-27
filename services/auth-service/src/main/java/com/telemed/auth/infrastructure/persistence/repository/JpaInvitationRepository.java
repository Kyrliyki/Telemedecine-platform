package com.telemed.auth.infrastructure.persistence.repository;

import com.telemed.auth.infrastructure.persistence.entity.InvitationEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaInvitationRepository extends JpaRepository<InvitationEntity, UUID> {
  Optional<InvitationEntity> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT i FROM InvitationEntity i WHERE i.tokenHash = :tokenHash")
  Optional<InvitationEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
