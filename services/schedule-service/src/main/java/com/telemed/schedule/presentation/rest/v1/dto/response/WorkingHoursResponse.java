package com.telemed.schedule.presentation.rest.v1.dto.response;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WorkingHoursResponse(
    UUID id,
    UUID doctorId,
    int dayOfWeek,
    LocalTime startTime,
    LocalTime endTime,
    int slotDurationMinutes,
    int bufferMinutes,
    String timezone,
    LocalDate effectiveFrom,
    LocalDate effectiveTo,
    String slotType,
    String location) {}
