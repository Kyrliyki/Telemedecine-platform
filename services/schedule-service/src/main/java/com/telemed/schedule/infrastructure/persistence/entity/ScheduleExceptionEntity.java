package com.telemed.schedule.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "schedule_exception")
public class ScheduleExceptionEntity {

  @Id private UUID id;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "starts_at", nullable = false)
  private Instant startsAt;

  @Column(name = "ends_at", nullable = false)
  private Instant endsAt;

  @Column(length = 255)
  private String reason;

  @Column(nullable = false, length = 16)
  private String recurrence;

  @Column(name = "recurrence_until")
  private Instant recurrenceUntil;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public void setDoctorId(UUID doctorId) {
    this.doctorId = doctorId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public void setStartsAt(Instant startsAt) {
    this.startsAt = startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public void setEndsAt(Instant endsAt) {
    this.endsAt = endsAt;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getRecurrence() {
    return recurrence;
  }

  public void setRecurrence(String recurrence) {
    this.recurrence = recurrence;
  }

  public Instant getRecurrenceUntil() {
    return recurrenceUntil;
  }

  public void setRecurrenceUntil(Instant recurrenceUntil) {
    this.recurrenceUntil = recurrenceUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
