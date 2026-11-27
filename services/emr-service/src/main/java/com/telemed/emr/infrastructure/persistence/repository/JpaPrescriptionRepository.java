package com.telemed.emr.infrastructure.persistence.repository;

import com.telemed.emr.infrastructure.persistence.entity.PrescriptionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPrescriptionRepository extends JpaRepository<PrescriptionEntity, UUID> {

  List<PrescriptionEntity> findAllByRecordIdOrderByCreatedAtAsc(UUID recordId);

  void deleteAllByRecordId(UUID recordId);
}
