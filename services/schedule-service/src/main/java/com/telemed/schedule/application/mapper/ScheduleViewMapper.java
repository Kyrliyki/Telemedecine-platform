package com.telemed.schedule.application.mapper;

import com.telemed.schedule.application.dto.view.ScheduleExceptionView;
import com.telemed.schedule.application.dto.view.SlotView;
import com.telemed.schedule.application.dto.view.WorkingHoursView;
import com.telemed.schedule.domain.model.ScheduleException;
import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.domain.model.WorkingHours;
import com.telemed.schedule.domain.valueobject.SlotStatus;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ScheduleViewMapper {

  public WorkingHoursView toView(WorkingHours hours) {
    return new WorkingHoursView(
        hours.getId(),
        hours.getDoctorId(),
        hours.getDayOfWeek(),
        hours.getStartTime(),
        hours.getEndTime(),
        hours.getSlotDurationMinutes(),
        hours.getBufferMinutes(),
        hours.getTimezone().getId(),
        hours.getEffectiveFrom(),
        hours.getEffectiveTo(),
        hours.getSlotType(),
        hours.getLocation());
  }

  public ScheduleExceptionView toView(ScheduleException exception) {
    return new ScheduleExceptionView(
        exception.getId(),
        exception.getDoctorId(),
        exception.getStartsAt(),
        exception.getEndsAt(),
        exception.getReason(),
        exception.getRecurrence().name(),
        exception.getRecurrenceUntil());
  }

  public SlotView toView(TimeSlot slot) {
    return new SlotView(
        slot.getId(),
        slot.getDoctorId(),
        slot.getStartsAt(),
        slot.getEndsAt(),
        slot.getStatus(),
        slot.getAppointmentId(),
        slot.getHoldExpiresAt(),
        slot.getSlotType(),
        slot.getLocation());
  }

  public SlotView virtualSlot(
      UUID doctorId,
      Instant start,
      Instant end,
      TimeSlot existing,
      String slotType,
      String location) {
    return new SlotView(
        existing == null ? null : existing.getId(),
        doctorId,
        start,
        end,
        existing == null ? SlotStatus.AVAILABLE : existing.getStatus(),
        existing == null ? null : existing.getAppointmentId(),
        existing == null ? null : existing.getHoldExpiresAt(),
        existing == null ? slotType : existing.getSlotType(),
        existing == null ? location : existing.getLocation());
  }
}
