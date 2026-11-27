package com.telemed.auth.infrastructure.persistence.repository;

import com.telemed.auth.infrastructure.persistence.entity.AuthAuditLogEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JpaAuthAuditLogRepository
    extends JpaRepository<AuthAuditLogEntity, UUID>, JpaSpecificationExecutor<AuthAuditLogEntity> {}
