package com.telemed.schedule.domain.model;

import com.telemed.schedule.domain.valueobject.ExceptionRecurrence;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class ScheduleException {

  private final UUID id;
  private final UUID doctorId;
  private final Instant startsAt;
  private final Instant endsAt;
  private final String reason;
  private final ExceptionRecurrence recurrence;
  private final Instant recurrenceUntil;
  private final Instant createdAt;

  private ScheduleException(
      UUID id,
      UUID doctorId,
      Instant startsAt,
      Instant endsAt,
      String reason,
      ExceptionRecurrence recurrence,
      Instant recurrenceUntil,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id required");
    this.doctorId = Objects.requireNonNull(doctorId, "doctorId required");
    this.startsAt = Objects.requireNonNull(startsAt, "startsAt required");
    this.endsAt = Objects.requireNonNull(endsAt, "endsAt required");
    if (!startsAt.isBefore(endsAt)) {
      throw new IllegalArgumentException("startsAt must be before endsAt");
    }
    this.reason = reason == null ? "" : reason.trim();
    this.recurrence = recurrence == null ? ExceptionRecurrence.NONE : recurrence;
    this.recurrenceUntil = recurrenceUntil;
    if (this.recurrence != ExceptionRecurrence.NONE && recurrenceUntil == null) {
      throw new IllegalArgumentException("recurrenceUntil is required for recurring exceptions");
    }
    if (recurrenceUntil != null && recurrenceUntil.isBefore(endsAt)) {
      throw new IllegalArgumentException("recurrenceUntil must not be before endsAt");
    }
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
  }

  public static ScheduleException create(
      UUID doctorId,
      Instant startsAt,
      Instant endsAt,
      String reason,
      ExceptionRecurrence recurrence,
      Instant recurrenceUntil,
      Instant now) {
    return new ScheduleException(
        UUID.randomUUID(), doctorId, startsAt, endsAt, reason, recurrence, recurrenceUntil, now);
  }

  public static ScheduleException rehydrate(
      UUID id,
      UUID doctorId,
      Instant startsAt,
      Instant endsAt,
      String reason,
      ExceptionRecurrence recurrence,
      Instant recurrenceUntil,
      Instant createdAt) {
    return new ScheduleException(
        id, doctorId, startsAt, endsAt, reason, recurrence, recurrenceUntil, createdAt);
  }

  public boolean blocks(Instant rangeStart, Instant rangeEnd) {
    if (recurrence == ExceptionRecurrence.NONE) {
      return overlaps(startsAt, endsAt, rangeStart, rangeEnd);
    }
    long stepSeconds =
        recurrence == ExceptionRecurrence.DAILY
            ? Duration.ofDays(1).toSeconds()
            : Duration.ofDays(7).toSeconds();
    long length = Duration.between(startsAt, endsAt).toSeconds();
    Instant until = recurrenceUntil == null ? endsAt : recurrenceUntil;
    Instant occurrence = startsAt;
    int guard = 0;
    while (!occurrence.isAfter(until) && occurrence.isBefore(rangeEnd) && guard++ < 10_000) {
      if (overlaps(occurrence, occurrence.plusSeconds(length), rangeStart, rangeEnd)) {
        return true;
      }
      occurrence = occurrence.plusSeconds(stepSeconds);
    }
    return false;
  }

  private static boolean overlaps(
      Instant start, Instant end, Instant rangeStart, Instant rangeEnd) {
    return start.isBefore(rangeEnd) && end.isAfter(rangeStart);
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

  public String getReason() {
    return reason;
  }

  public ExceptionRecurrence getRecurrence() {
    return recurrence;
  }

  public Instant getRecurrenceUntil() {
    return recurrenceUntil;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
