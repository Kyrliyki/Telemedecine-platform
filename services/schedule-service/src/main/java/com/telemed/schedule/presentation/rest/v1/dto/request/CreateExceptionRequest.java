package com.telemed.schedule.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateExceptionRequest(
    @NotNull Instant startsAt,
    @NotNull Instant endsAt,
    @Size(max = 255) String reason,
    String recurrence,
    Instant recurrenceUntil) {}
