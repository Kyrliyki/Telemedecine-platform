package com.telemed.emr.application.dto.view;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VitalSignsView(
    UUID id,
    BigDecimal heightCm,
    BigDecimal weightKg,
    Integer systolic,
    Integer diastolic,
    Integer pulse,
    BigDecimal temperatureC,
    Integer spo2,
    Instant recordedAt) {}
