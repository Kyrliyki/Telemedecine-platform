package com.telemed.emr.infrastructure.persistence.mapper;

import com.telemed.emr.domain.model.Prescription;
import com.telemed.emr.infrastructure.persistence.entity.PrescriptionEntity;
import org.springframework.stereotype.Component;

@Component
public class PrescriptionEntityMapper {

  public Prescription toDomain(PrescriptionEntity entity) {
    return new Prescription(
        entity.getId(),
        entity.getRecordId(),
        entity.getMedicationName(),
        entity.getDosage(),
        entity.getFrequency(),
        entity.getDurationDays(),
        entity.getInstructions(),
        entity.getCreatedAt());
  }

  public PrescriptionEntity toEntity(Prescription prescription) {
    PrescriptionEntity entity = new PrescriptionEntity();
    entity.setId(prescription.id());
    entity.setRecordId(prescription.recordId());
    entity.setMedicationName(prescription.medicationName());
    entity.setDosage(prescription.dosage());
    entity.setFrequency(prescription.frequency());
    entity.setDurationDays(prescription.durationDays());
    entity.setInstructions(prescription.instructions());
    entity.setCreatedAt(prescription.createdAt());
    return entity;
  }
}
