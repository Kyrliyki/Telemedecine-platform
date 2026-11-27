package com.telemed.schedule.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "doctor_working_hours")
public class WorkingHoursEntity {

  @Id private UUID id;

  @Column(name = "doctor_id", nullable = false)
  private UUID doctorId;

  @Column(name = "day_of_week", nullable = false)
  private int dayOfWeek;

  @Column(name = "start_time", nullable = false)
  private LocalTime startTime;

  @Column(name = "end_time", nullable = false)
  private LocalTime endTime;

  @Column(name = "slot_duration_minutes", nullable = false)
  private int slotDurationMinutes;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(name = "effective_from", nullable = false)
  private java.time.LocalDate effectiveFrom;

  @Column(name = "effective_to")
  private java.time.LocalDate effectiveTo;

  @Column(name = "buffer_minutes", nullable = false)
  private int bufferMinutes;

  @Column(name = "slot_type", nullable = false, length = 32)
  private String slotType;

  @Column(length = 120)
  private String location;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(nullable = false)
  private long version;

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

  public int getDayOfWeek() {
    return dayOfWeek;
  }

  public void setDayOfWeek(int dayOfWeek) {
    this.dayOfWeek = dayOfWeek;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalTime startTime) {
    this.startTime = startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public void setEndTime(LocalTime endTime) {
    this.endTime = endTime;
  }

  public int getSlotDurationMinutes() {
    return slotDurationMinutes;
  }

  public void setSlotDurationMinutes(int slotDurationMinutes) {
    this.slotDurationMinutes = slotDurationMinutes;
  }

  public String getTimezone() {
    return timezone;
  }

  public void setTimezone(String timezone) {
    this.timezone = timezone;
  }

  public java.time.LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public void setEffectiveFrom(java.time.LocalDate effectiveFrom) {
    this.effectiveFrom = effectiveFrom;
  }

  public java.time.LocalDate getEffectiveTo() {
    return effectiveTo;
  }

  public void setEffectiveTo(java.time.LocalDate effectiveTo) {
    this.effectiveTo = effectiveTo;
  }

  public int getBufferMinutes() {
    return bufferMinutes;
  }

  public void setBufferMinutes(int bufferMinutes) {
    this.bufferMinutes = bufferMinutes;
  }

  public String getSlotType() {
    return slotType;
  }

  public void setSlotType(String slotType) {
    this.slotType = slotType;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
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

  public long getVersion() {
    return version;
  }

  public void setVersion(long version) {
    this.version = version;
  }
}
