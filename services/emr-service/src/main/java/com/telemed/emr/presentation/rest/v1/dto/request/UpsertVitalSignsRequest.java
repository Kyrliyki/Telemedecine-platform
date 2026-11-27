package com.telemed.emr.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;

public record UpsertVitalSignsRequest(
    BigDecimal heightCm,
    BigDecimal weightKg,
    @Min(40) @Max(300) Integer systolic,
    @Min(20) @Max(200) Integer diastolic,
    @Min(20) @Max(300) Integer pulse,
    BigDecimal temperatureC,
    @Min(50) @Max(100) Integer spo2) {}
