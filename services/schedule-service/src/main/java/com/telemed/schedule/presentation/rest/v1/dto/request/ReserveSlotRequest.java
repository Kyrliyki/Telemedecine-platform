package com.telemed.schedule.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record ReserveSlotRequest(
    @NotNull UUID doctorId,
    @NotNull Instant startsAt,
    @NotNull UUID appointmentId,
    UUID patientId) {}
