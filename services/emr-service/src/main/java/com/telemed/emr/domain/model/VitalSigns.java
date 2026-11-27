package com.telemed.emr.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record VitalSigns(
    UUID id,
    UUID recordId,
    BigDecimal heightCm,
    BigDecimal weightKg,
    Integer systolic,
    Integer diastolic,
    Integer pulse,
    BigDecimal temperatureC,
    Integer spo2,
    Instant recordedAt) {

  public VitalSigns {
    Objects.requireNonNull(id, "id required");
    Objects.requireNonNull(recordId, "recordId required");
    if (systolic != null && (systolic < 40 || systolic > 300)) {
      throw new IllegalArgumentException("systolic is out of range");
    }
    if (diastolic != null && (diastolic < 20 || diastolic > 200)) {
      throw new IllegalArgumentException("diastolic is out of range");
    }
    if (pulse != null && (pulse < 20 || pulse > 300)) {
      throw new IllegalArgumentException("pulse is out of range");
    }
    if (spo2 != null && (spo2 < 50 || spo2 > 100)) {
      throw new IllegalArgumentException("spo2 is out of range");
    }
    Objects.requireNonNull(recordedAt, "recordedAt required");
  }

  public static VitalSigns create(
      UUID recordId,
      BigDecimal heightCm,
      BigDecimal weightKg,
      Integer systolic,
      Integer diastolic,
      Integer pulse,
      BigDecimal temperatureC,
      Integer spo2,
      Instant now) {
    return new VitalSigns(
        UUID.randomUUID(),
        recordId,
        heightCm,
        weightKg,
        systolic,
        diastolic,
        pulse,
        temperatureC,
        spo2,
        now);
  }
}
