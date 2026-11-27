package com.telemed.emr.infrastructure.persistence.repository;

import com.telemed.emr.infrastructure.persistence.entity.VitalSignsEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaVitalSignsRepository extends JpaRepository<VitalSignsEntity, UUID> {

  Optional<VitalSignsEntity> findByRecordId(UUID recordId);

  void deleteByRecordId(UUID recordId);
}
