package com.telemed.emr.infrastructure.persistence.mapper;

import com.telemed.emr.domain.model.MedicalRecord;
import com.telemed.emr.infrastructure.persistence.entity.MedicalRecordEntity;
import org.springframework.stereotype.Component;

@Component
public class MedicalRecordEntityMapper {

  public MedicalRecord toDomain(MedicalRecordEntity entity) {
    return MedicalRecord.rehydrate(
        entity.getId(),
        entity.getPatientId(),
        entity.getDoctorId(),
        entity.getAppointmentId(),
        entity.getStatus(),
        entity.getChiefComplaint(),
        entity.getDiagnosisCode(),
        entity.getDiagnosisText(),
        entity.getNotes(),
        entity.getTreatmentPlan(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getFinalizedAt(),
        entity.getDeletedAt(),
        entity.isLegalHold(),
        entity.getRetentionUntil(),
        entity.getVersion());
  }

  public MedicalRecordEntity toEntity(MedicalRecord record) {
    MedicalRecordEntity entity = new MedicalRecordEntity();
    entity.setId(record.getId());
    entity.setPatientId(record.getPatientId());
    entity.setDoctorId(record.getDoctorId());
    entity.setAppointmentId(record.getAppointmentId());
    entity.setStatus(record.getStatus());
    entity.setChiefComplaint(record.getChiefComplaint());
    entity.setDiagnosisCode(record.getDiagnosisCode());
    entity.setDiagnosisText(record.getDiagnosisText());
    entity.setNotes(record.getNotes());
    entity.setTreatmentPlan(record.getTreatmentPlan());
    entity.setCreatedAt(record.getCreatedAt());
    entity.setUpdatedAt(record.getUpdatedAt());
    entity.setFinalizedAt(record.getFinalizedAt());
    entity.setDeletedAt(record.getDeletedAt());
    entity.setLegalHold(record.isLegalHold());
    entity.setRetentionUntil(record.getRetentionUntil());
    entity.setVersion(record.getVersion());
    return entity;
  }
}
