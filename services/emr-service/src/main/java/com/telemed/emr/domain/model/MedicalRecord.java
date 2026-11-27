package com.telemed.emr.domain.model;

import com.telemed.emr.domain.valueobject.RecordStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class MedicalRecord {

  private final UUID id;
  private final UUID patientId;
  private final UUID doctorId;
  private final UUID appointmentId;
  private final Instant createdAt;

  private RecordStatus status;
  private String chiefComplaint;
  private String diagnosisCode;
  private String diagnosisText;
  private String notes;
  private String treatmentPlan;
  private Instant updatedAt;
  private Instant finalizedAt;
  private Instant deletedAt;
  private boolean legalHold;
  private Instant retentionUntil;
  private final long version;

  private MedicalRecord(
      UUID id,
      UUID patientId,
      UUID doctorId,
      UUID appointmentId,
      RecordStatus status,
      String chiefComplaint,
      String diagnosisCode,
      String diagnosisText,
      String notes,
      String treatmentPlan,
      Instant createdAt,
      Instant updatedAt,
      Instant finalizedAt,
      Instant deletedAt,
      boolean legalHold,
      Instant retentionUntil,
      long version) {
    this.id = Objects.requireNonNull(id, "id required");
    this.patientId = Objects.requireNonNull(patientId, "patientId required");
    this.doctorId = Objects.requireNonNull(doctorId, "doctorId required");
    this.appointmentId = appointmentId;
    this.status = Objects.requireNonNull(status, "status required");
    this.chiefComplaint = limit(chiefComplaint, 1000);
    this.diagnosisCode = limit(diagnosisCode, 32);
    this.diagnosisText = limit(diagnosisText, 1000);
    this.notes = notes;
    this.treatmentPlan = treatmentPlan;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
    this.finalizedAt = finalizedAt;
    this.deletedAt = deletedAt;
    this.legalHold = legalHold;
    this.retentionUntil = retentionUntil;
    this.version = version;
  }

  public static MedicalRecord draft(
      UUID patientId, UUID doctorId, UUID appointmentId, Instant now) {
    return new MedicalRecord(
        UUID.randomUUID(),
        patientId,
        doctorId,
        appointmentId,
        RecordStatus.DRAFT,
        null,
        null,
        null,
        null,
        null,
        now,
        now,
        null,
        null,
        false,
        null,
        0L);
  }

  public static MedicalRecord rehydrate(
      UUID id,
      UUID patientId,
      UUID doctorId,
      UUID appointmentId,
      RecordStatus status,
      String chiefComplaint,
      String diagnosisCode,
      String diagnosisText,
      String notes,
      String treatmentPlan,
      Instant createdAt,
      Instant updatedAt,
      Instant finalizedAt,
      Instant deletedAt,
      boolean legalHold,
      Instant retentionUntil,
      long version) {
    return new MedicalRecord(
        id,
        patientId,
        doctorId,
        appointmentId,
        status,
        chiefComplaint,
        diagnosisCode,
        diagnosisText,
        notes,
        treatmentPlan,
        createdAt,
        updatedAt,
        finalizedAt,
        deletedAt,
        legalHold,
        retentionUntil,
        version);
  }

  public void update(
      String chiefComplaint,
      String diagnosisCode,
      String diagnosisText,
      String notes,
      String treatmentPlan,
      Instant now) {
    requireEditable();
    if (chiefComplaint != null) {
      this.chiefComplaint = limit(chiefComplaint, 1000);
    }
    if (diagnosisCode != null) {
      this.diagnosisCode = limit(diagnosisCode, 32);
    }
    if (diagnosisText != null) {
      this.diagnosisText = limit(diagnosisText, 1000);
    }
    if (notes != null) {
      this.notes = notes;
    }
    if (treatmentPlan != null) {
      this.treatmentPlan = treatmentPlan;
    }
    this.updatedAt = now;
  }

  public void finalizeRecord(Instant now) {
    if (status == RecordStatus.FINALIZED) {
      return;
    }
    if (status != RecordStatus.DRAFT && status != RecordStatus.AMENDED) {
      throw new IllegalStateException("Only draft or amended records can be finalized");
    }
    this.status = RecordStatus.FINALIZED;
    this.finalizedAt = now;
    this.updatedAt = now;
  }

  public void amend(Instant now) {
    if (status != RecordStatus.FINALIZED) {
      throw new IllegalStateException("Only finalized records can be amended");
    }
    this.status = RecordStatus.AMENDED;
    this.updatedAt = now;
  }

  public void markDeleted(Instant now) {
    if (legalHold) {
      throw new IllegalStateException("Record is under legal hold");
    }
    this.status = RecordStatus.DELETED;
    this.chiefComplaint = null;
    this.diagnosisCode = null;
    this.diagnosisText = null;
    this.notes = null;
    this.treatmentPlan = null;
    this.deletedAt = now;
    this.updatedAt = now;
  }

  public boolean canBeEditedBy(UUID userId) {
    return doctorId.equals(userId) && status.isEditable();
  }

  public void setLegalHold(boolean legalHold, Instant now) {
    this.legalHold = legalHold;
    this.updatedAt = now;
  }

  public void setRetentionUntil(Instant retentionUntil, Instant now) {
    this.retentionUntil = retentionUntil;
    this.updatedAt = now;
  }

  public boolean isRetentionExpired(Instant now) {
    return !legalHold && retentionUntil != null && !retentionUntil.isAfter(now);
  }

  public boolean canBeViewedBy(UUID userId, boolean admin) {
    if (!status.isVisible()) {
      return false;
    }
    return admin || patientId.equals(userId) || doctorId.equals(userId);
  }

  public UUID getId() {
    return id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public String getChiefComplaint() {
    return chiefComplaint;
  }

  public String getDiagnosisCode() {
    return diagnosisCode;
  }

  public String getDiagnosisText() {
    return diagnosisText;
  }

  public String getNotes() {
    return notes;
  }

  public String getTreatmentPlan() {
    return treatmentPlan;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getFinalizedAt() {
    return finalizedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public boolean isLegalHold() {
    return legalHold;
  }

  public Instant getRetentionUntil() {
    return retentionUntil;
  }

  public long getVersion() {
    return version;
  }

  private void requireEditable() {
    if (!status.isEditable()) {
      throw new IllegalStateException("Record is not editable in status " + status);
    }
  }

  private static String limit(String value, int max) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw new IllegalArgumentException("Value exceeds " + max + " characters");
    }
    return trimmed;
  }
}
