package com.telemed.schedule.domain.model;

import com.telemed.schedule.domain.valueobject.SlotStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class TimeSlot {

  private final UUID id;
  private final UUID doctorId;
  private final Instant startsAt;
  private final Instant endsAt;
  private final Instant createdAt;

  private SlotStatus status;
  private UUID appointmentId;
  private Instant holdExpiresAt;
  private String slotType;
  private String location;
  private Instant updatedAt;
  private final long version;

  private TimeSlot(
      UUID id,
      UUID doctorId,
      Instant startsAt,
      Instant endsAt,
      SlotStatus status,
      UUID appointmentId,
      Instant holdExpiresAt,
      String slotType,
      String location,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id required");
    this.doctorId = Objects.requireNonNull(doctorId, "doctorId required");
    this.startsAt = Objects.requireNonNull(startsAt, "startsAt required");
    this.endsAt = Objects.requireNonNull(endsAt, "endsAt required");
    if (!startsAt.isBefore(endsAt)) {
      throw new IllegalArgumentException("startsAt must be before endsAt");
    }
    this.status = Objects.requireNonNull(status, "status required");
    this.appointmentId = appointmentId;
    this.holdExpiresAt = holdExpiresAt;
    if (status != SlotStatus.AVAILABLE && appointmentId == null) {
      throw new IllegalArgumentException("Reserved slot requires appointmentId");
    }
    if (status == SlotStatus.HELD && holdExpiresAt == null) {
      throw new IllegalArgumentException("Held slot requires holdExpiresAt");
    }
    this.slotType = slotType == null ? WorkingHours.DEFAULT_SLOT_TYPE : slotType;
    this.location = location;
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
    this.version = version;
  }

  public static TimeSlot held(
      UUID doctorId,
      Instant startsAt,
      Instant endsAt,
      UUID appointmentId,
      Instant holdExpiresAt,
      String slotType,
      String location,
      Instant now) {
    return new TimeSlot(
        UUID.randomUUID(),
        doctorId,
        startsAt,
        endsAt,
        SlotStatus.HELD,
        appointmentId,
        holdExpiresAt,
        slotType,
        location,
        now,
        now,
        0L);
  }

  public static TimeSlot rehydrate(
      UUID id,
      UUID doctorId,
      Instant startsAt,
      Instant endsAt,
      SlotStatus status,
      UUID appointmentId,
      Instant holdExpiresAt,
      String slotType,
      String location,
      Instant createdAt,
      Instant updatedAt,
      long version) {
    return new TimeSlot(
        id,
        doctorId,
        startsAt,
        endsAt,
        status,
        appointmentId,
        holdExpiresAt,
        slotType,
        location,
        createdAt,
        updatedAt,
        version);
  }

  public void hold(UUID appointmentId, Instant holdExpiresAt, Instant now) {
    if (status == SlotStatus.HELD || status == SlotStatus.CONFIRMED) {
      throw new IllegalStateException("Slot is already reserved");
    }
    this.status = SlotStatus.HELD;
    this.appointmentId = Objects.requireNonNull(appointmentId, "appointmentId required");
    this.holdExpiresAt = Objects.requireNonNull(holdExpiresAt, "holdExpiresAt required");
    this.updatedAt = now;
  }

  public void confirm(Instant now) {
    if (status == SlotStatus.CONFIRMED) {
      return;
    }
    if (status != SlotStatus.HELD) {
      throw new IllegalStateException("Only a held slot can be confirmed");
    }
    this.status = SlotStatus.CONFIRMED;
    this.holdExpiresAt = null;
    this.updatedAt = now;
  }

  public void release(Instant now) {
    this.status = SlotStatus.AVAILABLE;
    this.appointmentId = null;
    this.holdExpiresAt = null;
    this.updatedAt = now;
  }

  public boolean isHeld() {
    return status == SlotStatus.HELD;
  }

  public boolean isConfirmed() {
    return status == SlotStatus.CONFIRMED;
  }

  public boolean isExpired(Instant now) {
    return status == SlotStatus.HELD && holdExpiresAt != null && !holdExpiresAt.isAfter(now);
  }

  public UUID getId() {
    return id;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public SlotStatus getStatus() {
    return status;
  }

  public UUID getAppointmentId() {
    return appointmentId;
  }

  public Instant getHoldExpiresAt() {
    return holdExpiresAt;
  }

  public String getSlotType() {
    return slotType;
  }

  public String getLocation() {
    return location;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }
}
