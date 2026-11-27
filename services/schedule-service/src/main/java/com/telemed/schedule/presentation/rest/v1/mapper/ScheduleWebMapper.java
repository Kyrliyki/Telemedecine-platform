package com.telemed.schedule.presentation.rest.v1.mapper;

import com.telemed.schedule.application.dto.command.CreateScheduleExceptionCommand;
import com.telemed.schedule.application.dto.command.ReleaseSlotCommand;
import com.telemed.schedule.application.dto.command.ReserveSlotCommand;
import com.telemed.schedule.application.dto.command.UpdateWorkingHoursCommand;
import com.telemed.schedule.application.dto.view.ScheduleExceptionView;
import com.telemed.schedule.application.dto.view.SlotView;
import com.telemed.schedule.application.dto.view.WorkingHoursView;
import com.telemed.schedule.domain.valueobject.ExceptionRecurrence;
import com.telemed.schedule.presentation.rest.v1.dto.request.CreateExceptionRequest;
import com.telemed.schedule.presentation.rest.v1.dto.request.ReleaseSlotRequest;
import com.telemed.schedule.presentation.rest.v1.dto.request.ReserveSlotRequest;
import com.telemed.schedule.presentation.rest.v1.dto.request.UpdateWorkingHoursRequest;
import com.telemed.schedule.presentation.rest.v1.dto.response.ScheduleExceptionResponse;
import com.telemed.schedule.presentation.rest.v1.dto.response.SlotResponse;
import com.telemed.schedule.presentation.rest.v1.dto.response.WorkingHoursResponse;
import org.springframework.stereotype.Component;

@Component
public class ScheduleWebMapper {

  public WorkingHoursResponse toResponse(WorkingHoursView view) {
    return new WorkingHoursResponse(
        view.id(),
        view.doctorId(),
        view.dayOfWeek(),
        view.startTime(),
        view.endTime(),
        view.slotDurationMinutes(),
        view.bufferMinutes(),
        view.timezone(),
        view.effectiveFrom(),
        view.effectiveTo(),
        view.slotType(),
        view.location());
  }

  public ScheduleExceptionResponse toResponse(ScheduleExceptionView view) {
    return new ScheduleExceptionResponse(
        view.id(),
        view.doctorId(),
        view.startsAt(),
        view.endsAt(),
        view.reason(),
        view.recurrence(),
        view.recurrenceUntil());
  }

  public SlotResponse toResponse(SlotView view) {
    return new SlotResponse(
        view.id(),
        view.doctorId(),
        view.startsAt(),
        view.endsAt(),
        view.status().name(),
        view.appointmentId(),
        view.holdExpiresAt(),
        view.slotType(),
        view.location());
  }

  public UpdateWorkingHoursCommand toCommand(UpdateWorkingHoursRequest request) {
    return new UpdateWorkingHoursCommand(
        request.entries().stream()
            .map(
                entry ->
                    new UpdateWorkingHoursCommand.Entry(
                        entry.dayOfWeek(),
                        entry.startTime(),
                        entry.endTime(),
                        entry.slotDurationMinutes(),
                        entry.bufferMinutes(),
                        entry.timezone(),
                        entry.slotType(),
                        entry.location()))
            .toList(),
        request.effectiveFrom(),
        request.effectiveTo());
  }

  public CreateScheduleExceptionCommand toCommand(CreateExceptionRequest request) {
    ExceptionRecurrence recurrence =
        request.recurrence() == null || request.recurrence().isBlank()
            ? ExceptionRecurrence.NONE
            : ExceptionRecurrence.valueOf(
                request.recurrence().trim().toUpperCase(java.util.Locale.ROOT));
    return new CreateScheduleExceptionCommand(
        request.startsAt(),
        request.endsAt(),
        request.reason(),
        recurrence,
        request.recurrenceUntil());
  }

  public ReserveSlotCommand toCommand(ReserveSlotRequest request) {
    return new ReserveSlotCommand(
        request.doctorId(), request.startsAt(), request.appointmentId(), request.patientId());
  }

  public ReleaseSlotCommand toCommand(ReleaseSlotRequest request) {
    return new ReleaseSlotCommand(request.appointmentId());
  }
}
