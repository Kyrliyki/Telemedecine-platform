package com.telemed.emr.infrastructure.persistence.repository;

import com.telemed.emr.domain.valueobject.RecordStatus;
import com.telemed.emr.infrastructure.persistence.entity.MedicalRecordEntity;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaMedicalRecordRepository extends JpaRepository<MedicalRecordEntity, UUID> {

  Optional<MedicalRecordEntity> findByAppointmentId(UUID appointmentId);

  Page<MedicalRecordEntity> findByPatientId(UUID patientId, Pageable pageable);

  Page<MedicalRecordEntity> findByPatientIdAndStatus(
      UUID patientId, RecordStatus status, Pageable pageable);

  Page<MedicalRecordEntity> findByDoctorId(UUID doctorId, Pageable pageable);

  Page<MedicalRecordEntity> findByDoctorIdAndStatus(
      UUID doctorId, RecordStatus status, Pageable pageable);

  Page<MedicalRecordEntity> findByPatientIdAndDoctorId(
      UUID patientId, UUID doctorId, Pageable pageable);

  Page<MedicalRecordEntity> findByPatientIdAndDoctorIdAndStatus(
      UUID patientId, UUID doctorId, RecordStatus status, Pageable pageable);

  @Modifying
  @Query(
      """
      UPDATE MedicalRecordEntity r
      SET r.status = com.telemed.emr.domain.valueobject.RecordStatus.DELETED,
          r.chiefComplaint = NULL,
          r.diagnosisCode = NULL,
          r.diagnosisText = NULL,
          r.notes = NULL,
          r.treatmentPlan = NULL,
          r.deletedAt = :now,
          r.updatedAt = :now
      WHERE r.patientId = :patientId
        AND r.legalHold = FALSE
      """)
  int markDeletedByPatient(@Param("patientId") UUID patientId, @Param("now") Instant now);

  @Query(
      "SELECT r.id FROM MedicalRecordEntity r WHERE r.patientId = :patientId AND r.legalHold = FALSE")
  java.util.List<UUID> findIdsByPatient(@Param("patientId") UUID patientId);

  @Query(
      "SELECT r.id FROM MedicalRecordEntity r WHERE r.doctorId = :doctorId AND r.legalHold = FALSE")
  java.util.List<UUID> findIdsByDoctor(@Param("doctorId") UUID doctorId);

  @Query(
      """
      SELECT r.id FROM MedicalRecordEntity r
      WHERE r.legalHold = FALSE
        AND r.retentionUntil IS NOT NULL
        AND r.retentionUntil <= :now
      """)
  java.util.List<UUID> findExpiredIds(
      @Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

  @Modifying
  @Query(
      """
      UPDATE MedicalRecordEntity r
      SET r.status = com.telemed.emr.domain.valueobject.RecordStatus.DELETED,
          r.chiefComplaint = NULL,
          r.diagnosisCode = NULL,
          r.diagnosisText = NULL,
          r.notes = NULL,
          r.treatmentPlan = NULL,
          r.deletedAt = :now,
          r.updatedAt = :now
      WHERE r.doctorId = :doctorId
        AND r.legalHold = FALSE
      """)
  int markDeletedByDoctor(@Param("doctorId") UUID doctorId, @Param("now") Instant now);
}
