package com.telemed.emr.application.dto.command;

import java.math.BigDecimal;

public record UpsertVitalSignsCommand(
    BigDecimal heightCm,
    BigDecimal weightKg,
    Integer systolic,
    Integer diastolic,
    Integer pulse,
    BigDecimal temperatureC,
    Integer spo2) {}
