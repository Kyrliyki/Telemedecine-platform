package com.telemed.schedule.application.dto.command;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record UpdateWorkingHoursCommand(
    List<Entry> entries, LocalDate effectiveFrom, LocalDate effectiveTo) {

  public record Entry(
      int dayOfWeek,
      LocalTime startTime,
      LocalTime endTime,
      int slotDurationMinutes,
      int bufferMinutes,
      String timezone,
      String slotType,
      String location) {}
}
