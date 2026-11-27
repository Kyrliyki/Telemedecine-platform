package com.telemed.emr.infrastructure.persistence.mapper;

import com.telemed.emr.domain.model.MedicalAttachment;
import com.telemed.emr.infrastructure.persistence.entity.MedicalAttachmentEntity;
import org.springframework.stereotype.Component;

@Component
public class MedicalAttachmentEntityMapper {

  public MedicalAttachment toDomain(MedicalAttachmentEntity entity) {
    return new MedicalAttachment(
        entity.getId(),
        entity.getRecordId(),
        entity.getFileId(),
        entity.getDescription(),
        entity.getCreatedAt());
  }

  public MedicalAttachmentEntity toEntity(MedicalAttachment attachment) {
    MedicalAttachmentEntity entity = new MedicalAttachmentEntity();
    entity.setId(attachment.id());
    entity.setRecordId(attachment.recordId());
    entity.setFileId(attachment.fileId());
    entity.setDescription(attachment.description());
    entity.setCreatedAt(attachment.createdAt());
    return entity;
  }
}
