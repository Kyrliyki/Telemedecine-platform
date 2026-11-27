package com.telemed.emr.presentation.rest.v1.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record VitalSignsResponse(
    UUID id,
    BigDecimal heightCm,
    BigDecimal weightKg,
    Integer systolic,
    Integer diastolic,
    Integer pulse,
    BigDecimal temperatureC,
    Integer spo2,
    Instant recordedAt) {}
