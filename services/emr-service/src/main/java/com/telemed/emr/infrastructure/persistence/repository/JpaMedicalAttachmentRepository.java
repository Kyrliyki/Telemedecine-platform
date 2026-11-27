package com.telemed.emr.infrastructure.persistence.repository;

import com.telemed.emr.infrastructure.persistence.entity.MedicalAttachmentEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaMedicalAttachmentRepository
    extends JpaRepository<MedicalAttachmentEntity, UUID> {

  List<MedicalAttachmentEntity> findAllByRecordIdOrderByCreatedAtAsc(UUID recordId);

  void deleteAllByRecordId(UUID recordId);
}
