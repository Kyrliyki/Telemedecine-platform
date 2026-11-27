package com.telemed.common.events;

import java.time.Instant;
import java.util.UUID;

/** Shared payloads for emr-service integration events. */
public final class EmrEventPayloads {

    private EmrEventPayloads() {
    }

    public record MedicalRecordPayload(
            UUID recordId,
            UUID patientId,
            UUID doctorId,
            UUID appointmentId,
            String status,
            Instant occurredAt
    ) {
    }

    public record MedicalRecordDeletedPayload(
            UUID recordId,
            UUID patientId,
            UUID doctorId,
            Instant deletedAt
    ) {
    }
}
