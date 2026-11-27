package com.telemed.schedule.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record ScheduleExceptionView(
    UUID id,
    UUID doctorId,
    Instant startsAt,
    Instant endsAt,
    String reason,
    String recurrence,
    Instant recurrenceUntil) {}
