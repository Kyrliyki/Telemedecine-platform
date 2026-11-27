package com.telemed.common.events;

import java.time.Instant;
import java.util.UUID;

/** Shared payloads for schedule-service integration events. */
public final class ScheduleEventPayloads {

    private ScheduleEventPayloads() {
    }

    public record SlotReservedPayload(
            UUID slotId,
            UUID doctorId,
            UUID appointmentId,
            Instant startsAt,
            Instant endsAt
    ) {
    }

    public record SlotReleasedPayload(
            UUID slotId,
            UUID doctorId,
            UUID appointmentId,
            Instant startsAt,
            Instant releasedAt
    ) {
    }

    public record SlotConfirmedPayload(
            UUID slotId,
            UUID doctorId,
            UUID appointmentId,
            Instant startsAt,
            Instant endsAt,
            Instant confirmedAt
    ) {
    }

    public record SlotHoldExpiredPayload(
            UUID slotId,
            UUID doctorId,
            UUID appointmentId,
            Instant startsAt,
            Instant expiredAt
    ) {
    }

    public record AvailabilityChangedPayload(
            UUID doctorId,
            String reason,
            Instant changedAt,
            Instant effectiveFrom
    ) {
    }
}
