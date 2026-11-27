package com.telemed.schedule.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ScheduleExceptionResponse(
    UUID id,
    UUID doctorId,
    Instant startsAt,
    Instant endsAt,
    String reason,
    String recurrence,
    Instant recurrenceUntil) {}
