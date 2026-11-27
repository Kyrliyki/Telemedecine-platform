package com.telemed.emr.application.port.output;

import com.telemed.emr.domain.model.MedicalAttachment;
import java.util.List;
import java.util.UUID;

public interface AttachmentRepository {

  List<MedicalAttachment> findByRecordId(UUID recordId);

  MedicalAttachment save(MedicalAttachment attachment);

  void deleteByRecordId(UUID recordId);
}
