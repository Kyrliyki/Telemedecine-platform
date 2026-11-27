package com.telemed.schedule.application.dto.view;

import com.telemed.schedule.domain.valueobject.SlotStatus;
import java.time.Instant;
import java.util.UUID;

public record SlotView(
    UUID id,
    UUID doctorId,
    Instant startsAt,
    Instant endsAt,
    SlotStatus status,
    UUID appointmentId,
    Instant holdExpiresAt,
    String slotType,
    String location) {}
