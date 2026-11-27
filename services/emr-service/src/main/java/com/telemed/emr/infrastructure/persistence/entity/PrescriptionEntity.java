package com.telemed.emr.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "prescription")
public class PrescriptionEntity {

  @Id private UUID id;

  @Column(name = "record_id", nullable = false)
  private UUID recordId;

  @Column(name = "medication_name", nullable = false, length = 255)
  private String medicationName;

  @Column(nullable = false, length = 120)
  private String dosage;

  @Column(nullable = false, length = 120)
  private String frequency;

  @Column(name = "duration_days", nullable = false)
  private int durationDays;

  @Column(length = 1000)
  private String instructions;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getRecordId() {
    return recordId;
  }

  public void setRecordId(UUID recordId) {
    this.recordId = recordId;
  }

  public String getMedicationName() {
    return medicationName;
  }

  public void setMedicationName(String medicationName) {
    this.medicationName = medicationName;
  }

  public String getDosage() {
    return dosage;
  }

  public void setDosage(String dosage) {
    this.dosage = dosage;
  }

  public String getFrequency() {
    return frequency;
  }

  public void setFrequency(String frequency) {
    this.frequency = frequency;
  }

  public int getDurationDays() {
    return durationDays;
  }

  public void setDurationDays(int durationDays) {
    this.durationDays = durationDays;
  }

  public String getInstructions() {
    return instructions;
  }

  public void setInstructions(String instructions) {
    this.instructions = instructions;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
