package com.telemed.emr.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "medical_record_revision")
public class MedicalRecordRevisionEntity {

  @Id private UUID id;

  @Column(name = "record_id", nullable = false)
  private UUID recordId;

  @Column(name = "revision_number", nullable = false)
  private int revisionNumber;

  @Column(nullable = false, columnDefinition = "text")
  private String snapshot;

  @Column(name = "changed_by")
  private UUID changedBy;

  @Column(name = "change_reason", length = 255)
  private String changeReason;

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

  public int getRevisionNumber() {
    return revisionNumber;
  }

  public void setRevisionNumber(int revisionNumber) {
    this.revisionNumber = revisionNumber;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public void setSnapshot(String snapshot) {
    this.snapshot = snapshot;
  }

  public UUID getChangedBy() {
    return changedBy;
  }

  public void setChangedBy(UUID changedBy) {
    this.changedBy = changedBy;
  }

  public String getChangeReason() {
    return changeReason;
  }

  public void setChangeReason(String changeReason) {
    this.changeReason = changeReason;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
