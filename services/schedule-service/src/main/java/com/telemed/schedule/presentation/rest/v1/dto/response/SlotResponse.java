package com.telemed.schedule.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SlotResponse(
    UUID id,
    UUID doctorId,
    Instant startsAt,
    Instant endsAt,
    String status,
    UUID appointmentId,
    Instant holdExpiresAt,
    String slotType,
    String location) {}
