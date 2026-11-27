package com.telemed.emr.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionResponse(
    UUID id,
    String medicationName,
    String dosage,
    String frequency,
    int durationDays,
    String instructions,
    Instant createdAt) {}
