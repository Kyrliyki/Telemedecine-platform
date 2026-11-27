package com.telemed.emr.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record MedicalRecordRevisionView(
    UUID id,
    UUID recordId,
    int revisionNumber,
    UUID changedBy,
    String changeReason,
    String snapshot,
    Instant createdAt) {}
