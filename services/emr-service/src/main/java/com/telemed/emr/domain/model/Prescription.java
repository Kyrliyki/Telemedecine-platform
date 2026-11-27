package com.telemed.emr.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Prescription(
    UUID id,
    UUID recordId,
    String medicationName,
    String dosage,
    String frequency,
    int durationDays,
    String instructions,
    Instant createdAt) {

  public Prescription {
    Objects.requireNonNull(id, "id required");
    Objects.requireNonNull(recordId, "recordId required");
    medicationName = require(medicationName, "medicationName", 255);
    dosage = require(dosage, "dosage", 120);
    frequency = require(frequency, "frequency", 120);
    if (durationDays < 1 || durationDays > 365) {
      throw new IllegalArgumentException("durationDays must be between 1 and 365");
    }
    instructions = instructions == null ? null : instructions.trim();
    Objects.requireNonNull(createdAt, "createdAt required");
  }

  public static Prescription create(
      UUID recordId,
      String medicationName,
      String dosage,
      String frequency,
      int durationDays,
      String instructions,
      Instant now) {
    return new Prescription(
        UUID.randomUUID(),
        recordId,
        medicationName,
        dosage,
        frequency,
        durationDays,
        instructions,
        now);
  }

  private static String require(String value, String field, int max) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required");
    }
    String trimmed = value.trim();
    if (trimmed.length() > max) {
      throw new IllegalArgumentException(field + " is too long");
    }
    return trimmed;
  }
}
