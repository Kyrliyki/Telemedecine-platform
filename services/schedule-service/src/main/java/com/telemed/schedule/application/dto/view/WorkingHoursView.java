package com.telemed.schedule.application.dto.view;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record WorkingHoursView(
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
