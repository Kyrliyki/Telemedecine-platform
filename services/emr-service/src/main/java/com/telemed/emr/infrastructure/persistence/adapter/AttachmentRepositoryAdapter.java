package com.telemed.emr.infrastructure.persistence.adapter;

import com.telemed.emr.application.port.output.AttachmentRepository;
import com.telemed.emr.domain.model.MedicalAttachment;
import com.telemed.emr.infrastructure.persistence.mapper.MedicalAttachmentEntityMapper;
import com.telemed.emr.infrastructure.persistence.repository.JpaMedicalAttachmentRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AttachmentRepositoryAdapter implements AttachmentRepository {

  private final JpaMedicalAttachmentRepository jpa;
  private final MedicalAttachmentEntityMapper mapper;

  public AttachmentRepositoryAdapter(
      JpaMedicalAttachmentRepository jpa, MedicalAttachmentEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public List<MedicalAttachment> findByRecordId(UUID recordId) {
    return jpa.findAllByRecordIdOrderByCreatedAtAsc(recordId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public MedicalAttachment save(MedicalAttachment attachment) {
    return mapper.toDomain(jpa.save(mapper.toEntity(attachment)));
  }

  @Override
  public void deleteByRecordId(UUID recordId) {
    jpa.deleteAllByRecordId(recordId);
  }
}
