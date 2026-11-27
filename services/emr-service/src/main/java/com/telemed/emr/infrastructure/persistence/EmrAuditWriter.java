package com.telemed.emr.infrastructure.persistence;

import com.telemed.emr.infrastructure.persistence.entity.EmrAuditLogEntity;
import com.telemed.emr.infrastructure.persistence.repository.JpaEmrAuditLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class EmrAuditWriter {

  private final JpaEmrAuditLogRepository repository;

  public EmrAuditWriter(JpaEmrAuditLogRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void save(EmrAuditLogEntity entity) {
    repository.save(entity);
  }
}
