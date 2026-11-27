package com.telemed.common.events;

import java.time.Instant;
import java.util.UUID;

/** Shared payloads for appointment-service integration events. */
public final class AppointmentEventPayloads {

    private AppointmentEventPayloads() {
    }

    public record AppointmentCreatedPayload(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant startsAt,
            Instant endsAt
    ) {
    }

    public record AppointmentConfirmedPayload(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant startsAt,
            Instant endsAt
    ) {
    }

    public record AppointmentNoShowPayload(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant startsAt,
            Instant endsAt
    ) {
    }

    public record AppointmentCancelledPayload(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            String reason
    ) {
    }

    public record AppointmentRescheduledPayload(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant previousStartsAt,
            Instant startsAt,
            Instant endsAt,
            String reason
    ) {
    }

    public record AppointmentCompletedPayload(
            UUID appointmentId,
            UUID patientId,
            UUID doctorId,
            Instant completedAt
    ) {
    }
}
