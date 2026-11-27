package com.telemed.emr.infrastructure.persistence.entity;

import com.telemed.emr.domain.valueobject.RecordStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medical_record")
public class MedicalRecordEntity {

  @Id private UUID id;

  @Column(name = "patient_id", nullable = false)
  private UUID patientId;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "appointment_id")
  private UUID appointmentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private RecordStatus status;

  @Column(name = "chief_complaint", length = 1000)
  private String chiefComplaint;

  @Column(name = "diagnosis_code", length = 32)
  private String diagnosisCode;

  @Column(name = "diagnosis_text", length = 1000)
  private String diagnosisText;

  @Column(columnDefinition = "text")
  private String notes;

  @Column(name = "treatment_plan", columnDefinition = "text")
  private String treatmentPlan;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "finalized_at")
  private Instant finalizedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "legal_hold", nullable = false)
  private boolean legalHold;

  @Column(name = "retention_until")
  private Instant retentionUntil;

  @Version
  @Column(nullable = false)
  private long version;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getPatientId() {
    return patientId;
  }

  public void setPatientId(UUID patientId) {
    this.patientId = patientId;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(UUID doctorId) {
    this.doctorId = doctorId;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public void setAppointmentId(UUID appointmentId) {
    this.appointmentId = appointmentId;
  }

  public RecordStatus getStatus() {
    return status;
  }

  public void setStatus(RecordStatus status) {
    this.status = status;
  }

  public String getChiefComplaint() {
    return chiefComplaint;
  }

  public void setChiefComplaint(String chiefComplaint) {
    this.chiefComplaint = chiefComplaint;
  }

  public String getDiagnosisCode() {
    return diagnosisCode;
  }

  public void setDiagnosisCode(String diagnosisCode) {
    this.diagnosisCode = diagnosisCode;
  }

  public String getDiagnosisText() {
    return diagnosisText;
  }

  public void setDiagnosisText(String diagnosisText) {
    this.diagnosisText = diagnosisText;
  }

  public String getNotes() {
    return notes;
  }

  public void setNotes(String notes) {
    this.notes = notes;
  }

  public String getTreatmentPlan() {
    return treatmentPlan;
  }

  public void setTreatmentPlan(String treatmentPlan) {
    this.treatmentPlan = treatmentPlan;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }

  public Instant getFinalizedAt() {
    return finalizedAt;
  }

  public void setFinalizedAt(Instant finalizedAt) {
    this.finalizedAt = finalizedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public void setDeletedAt(Instant deletedAt) {
    this.deletedAt = deletedAt;
  }

  public boolean isLegalHold() {
    return legalHold;
  }

  public void setLegalHold(boolean legalHold) {
    this.legalHold = legalHold;
  }

  public Instant getRetentionUntil() {
    return retentionUntil;
  }

  public void setRetentionUntil(Instant retentionUntil) {
    this.retentionUntil = retentionUntil;
  }

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
