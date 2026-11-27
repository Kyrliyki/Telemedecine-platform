package com.telemed.emr.application.port.output;

import com.telemed.emr.domain.model.Prescription;
import java.util.List;
import java.util.UUID;

public interface PrescriptionRepository {

  List<Prescription> findByRecordId(UUID recordId);

  List<Prescription> replaceForRecord(UUID recordId, List<Prescription> prescriptions);

  void deleteByRecordId(UUID recordId);
}
