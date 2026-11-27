package com.telemed.schedule.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.UUID;

public final class WorkingHours {

  public static final String DEFAULT_SLOT_TYPE = "STANDARD";

  private final UUID id;
  private final UUID doctorId;
  private final Instant createdAt;
  private final int dayOfWeek;
  private final LocalTime startTime;
  private final LocalTime endTime;

  private LocalDate effectiveFrom;
  private LocalDate effectiveTo;
  private int slotDurationMinutes;
  private int bufferMinutes;
  private String slotType;
  private String location;
  private ZoneId timezone;
  private Instant updatedAt;

  private WorkingHours(
      UUID id,
      UUID doctorId,
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      int slotDurationMinutes,
      int bufferMinutes,
      ZoneId timezone,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String slotType,
      String location,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id required");
    this.doctorId = Objects.requireNonNull(doctorId, "doctorId required");
    this.dayOfWeek = validateDay(dayOfWeek);
    this.startTime = Objects.requireNonNull(startTime, "startTime required");
    this.endTime = Objects.requireNonNull(endTime, "endTime required");
    if (!startTime.isBefore(endTime)) {
      throw new IllegalArgumentException("startTime must be before endTime");
    }
    this.slotDurationMinutes = validateDuration(slotDurationMinutes);
    this.bufferMinutes = validateBuffer(bufferMinutes);
    this.timezone = Objects.requireNonNull(timezone, "timezone required");
    this.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom required");
    this.effectiveTo = effectiveTo;
    if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom");
    }
    this.slotType = normalizeType(slotType);
    this.location = normalizeLocation(location);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
  }

  public static WorkingHours create(
      UUID doctorId,
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      int slotDurationMinutes,
      int bufferMinutes,
      ZoneId timezone,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String slotType,
      String location,
      Instant now) {
    return new WorkingHours(
        UUID.randomUUID(),
        doctorId,
        dayOfWeek,
        startTime,
        endTime,
        slotDurationMinutes,
        bufferMinutes,
        timezone,
        effectiveFrom,
        effectiveTo,
        slotType,
        location,
        now,
        now);
  }

  public static WorkingHours rehydrate(
      UUID id,
      UUID doctorId,
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      int slotDurationMinutes,
      int bufferMinutes,
      ZoneId timezone,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String slotType,
      String location,
      Instant createdAt,
      Instant updatedAt) {
    return new WorkingHours(
        id,
        doctorId,
        dayOfWeek,
        startTime,
        endTime,
        slotDurationMinutes,
        bufferMinutes,
        timezone,
        effectiveFrom,
        effectiveTo,
        slotType,
        location,
        createdAt,
        updatedAt);
  }

  public boolean isEffectiveOn(LocalDate date) {
    return !date.isBefore(effectiveFrom) && (effectiveTo == null || !date.isAfter(effectiveTo));
  }

  public ZonedDateTime startOn(LocalDate date) {
    return ZonedDateTime.of(date, startTime, timezone);
  }

  public ZonedDateTime endOn(LocalDate date) {
    return ZonedDateTime.of(date, endTime, timezone);
  }

  public UUID getId() {
    return id;
  }

  public UUID getDoctorId() {
    return doctorId;
  }

  public int getDayOfWeek() {
    return dayOfWeek;
  }

  public LocalTime getStartTime() {
    return startTime;
  }

  public LocalTime getEndTime() {
    return endTime;
  }

  public int getSlotDurationMinutes() {
    return slotDurationMinutes;
  }

  public int getBufferMinutes() {
    return bufferMinutes;
  }

  public ZoneId getTimezone() {
    return timezone;
  }

  public LocalDate getEffectiveFrom() {
    return effectiveFrom;
  }

  public LocalDate getEffectiveTo() {
    return effectiveTo;
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

  private static int validateDay(int value) {
    if (value < 1 || value > 7) {
      throw new IllegalArgumentException("dayOfWeek must be between 1 and 7");
    }
    return value;
  }

  private static int validateDuration(int value) {
    if (value < 5 || value > 240) {
      throw new IllegalArgumentException("slotDurationMinutes must be between 5 and 240");
    }
    return value;
  }

  private static int validateBuffer(int value) {
    if (value < 0 || value > 120) {
      throw new IllegalArgumentException("bufferMinutes must be between 0 and 120");
    }
    return value;
  }

  private static String normalizeType(String value) {
    String type =
        value == null || value.isBlank()
            ? DEFAULT_SLOT_TYPE
            : value.trim().toUpperCase(java.util.Locale.ROOT);
    if (!type.matches("[A-Z_]{2,32}")) {
      throw new IllegalArgumentException("slotType is invalid");
    }
    return type;
  }

  private static String normalizeLocation(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > 120) {
      throw new IllegalArgumentException("location is too long");
    }
    return trimmed;
  }
}
