package com.telemed.emr.infrastructure.persistence.repository;

import com.telemed.emr.infrastructure.persistence.entity.MedicalRecordRevisionEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMedicalRecordRevisionRepository
    extends JpaRepository<MedicalRecordRevisionEntity, UUID> {

  List<MedicalRecordRevisionEntity> findAllByRecordIdOrderByRevisionNumberAsc(UUID recordId);

  Optional<MedicalRecordRevisionEntity> findByRecordIdAndRevisionNumber(
      UUID recordId, int revisionNumber);

  @Query(
      "SELECT COALESCE(MAX(r.revisionNumber), 0) FROM MedicalRecordRevisionEntity r WHERE r.recordId = :recordId")
  int maxRevisionNumber(@Param("recordId") UUID recordId);
}
