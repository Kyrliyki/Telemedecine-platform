package com.telemed.emr.infrastructure.persistence.repository;

import com.telemed.emr.infrastructure.persistence.entity.EmrAuditLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaEmrAuditLogRepository extends JpaRepository<EmrAuditLogEntity, UUID> {

  List<EmrAuditLogEntity> findAllByRecordIdOrderByCreatedAtAsc(UUID recordId);
}
