package com.telemed.schedule.application.dto.command;

import com.telemed.schedule.domain.valueobject.ExceptionRecurrence;
import java.time.Instant;

public record CreateScheduleExceptionCommand(
    Instant startsAt,
    Instant endsAt,
    String reason,
    ExceptionRecurrence recurrence,
    Instant recurrenceUntil) {}
