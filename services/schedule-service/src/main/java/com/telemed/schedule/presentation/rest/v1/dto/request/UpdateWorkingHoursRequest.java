package com.telemed.schedule.presentation.rest.v1.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record UpdateWorkingHoursRequest(
    @NotEmpty List<@Valid Entry> entries, @NotNull LocalDate effectiveFrom, LocalDate effectiveTo) {

  public record Entry(
      @Min(1) @Max(7) int dayOfWeek,
      @NotNull LocalTime startTime,
      @NotNull LocalTime endTime,
      @Min(5) @Max(240) int slotDurationMinutes,
      @Min(0) @Max(120) int bufferMinutes,
      @NotBlank String timezone,
      @Size(max = 32) String slotType,
      @Size(max = 120) String location) {}
}
