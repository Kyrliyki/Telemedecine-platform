package com.telemed.emr.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionView(
    UUID id,
    String medicationName,
    String dosage,
    String frequency,
    int durationDays,
    String instructions,
    Instant createdAt) {}
