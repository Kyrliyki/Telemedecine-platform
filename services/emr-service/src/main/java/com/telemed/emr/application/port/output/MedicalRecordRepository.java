package com.telemed.emr.application.port.output;

import com.telemed.emr.application.dto.view.PageResult;
import com.telemed.emr.domain.model.MedicalRecord;
import com.telemed.emr.domain.valueobject.RecordStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository {

  MedicalRecord save(MedicalRecord record);

  Optional<MedicalRecord> findById(UUID id);

  Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

  PageResult<MedicalRecord> findByPatient(UUID patientId, RecordStatus status, int page, int size);

  PageResult<MedicalRecord> findByDoctor(UUID doctorId, RecordStatus status, int page, int size);

  PageResult<MedicalRecord> findByPatientAndDoctor(
      UUID patientId, UUID doctorId, RecordStatus status, int page, int size);

  int markDeletedByPatient(UUID patientId, Instant now);

  java.util.List<UUID> findIdsByPatient(UUID patientId);

  java.util.List<UUID> findIdsByDoctor(UUID doctorId);

  java.util.List<UUID> findExpiredIds(Instant now, int limit);

  void deleteById(UUID id);

  int markDeletedByDoctor(UUID doctorId, Instant now);
}
