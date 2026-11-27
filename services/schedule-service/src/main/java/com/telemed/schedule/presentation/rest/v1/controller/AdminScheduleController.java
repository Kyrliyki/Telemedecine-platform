package com.telemed.schedule.presentation.rest.v1.controller;

import com.telemed.schedule.application.service.AvailabilityService;
import com.telemed.schedule.application.service.ScheduleExceptionService;
import com.telemed.schedule.application.service.SlotReservationService;
import com.telemed.schedule.application.service.WorkingHoursService;
import com.telemed.schedule.presentation.rest.v1.dto.request.CreateExceptionRequest;
import com.telemed.schedule.presentation.rest.v1.dto.request.UpdateWorkingHoursRequest;
import com.telemed.schedule.presentation.rest.v1.dto.response.ReleaseResponse;
import com.telemed.schedule.presentation.rest.v1.dto.response.ScheduleExceptionResponse;
import com.telemed.schedule.presentation.rest.v1.dto.response.SlotResponse;
import com.telemed.schedule.presentation.rest.v1.dto.response.WorkingHoursResponse;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/schedule/doctors/{doctorId}")
@PreAuthorize("hasRole('ADMIN')")
public class AdminScheduleController {

  private final WorkingHoursService workingHoursService;
  private final ScheduleExceptionService exceptionService;
  private final AvailabilityService availabilityService;
  private final SlotReservationService reservationService;
  private final ScheduleWebMapper mapper;

  public AdminScheduleController(
      WorkingHoursService workingHoursService,
      ScheduleExceptionService exceptionService,
      AvailabilityService availabilityService,
      SlotReservationService reservationService,
      ScheduleWebMapper mapper) {
    this.workingHoursService = workingHoursService;
    this.exceptionService = exceptionService;
    this.availabilityService = availabilityService;
    this.reservationService = reservationService;
    this.mapper = mapper;
  }

  @GetMapping("/working-hours")
  public List<WorkingHoursResponse> workingHours(@PathVariable UUID doctorId) {
    return workingHoursService.get(doctorId).stream().map(mapper::toResponse).toList();
  }

  @PutMapping("/working-hours")
  public ResponseEntity<List<WorkingHoursResponse>> replaceWorkingHours(
      @PathVariable UUID doctorId, @Valid @RequestBody UpdateWorkingHoursRequest request) {
    return ResponseEntity.ok(
        workingHoursService.replace(doctorId, mapper.toCommand(request)).stream()
            .map(mapper::toResponse)
            .toList());
  }

  @GetMapping("/exceptions")
  public List<ScheduleExceptionResponse> exceptions(@PathVariable UUID doctorId) {
    return exceptionService.list(doctorId).stream().map(mapper::toResponse).toList();
  }

  @PostMapping("/exceptions")
  public ResponseEntity<ScheduleExceptionResponse> createException(
      @PathVariable UUID doctorId, @Valid @RequestBody CreateExceptionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(exceptionService.create(doctorId, mapper.toCommand(request))));
  }

  @DeleteMapping("/exceptions/{exceptionId}")
  public ResponseEntity<Void> deleteException(
      @PathVariable UUID doctorId, @PathVariable UUID exceptionId) {
    exceptionService.delete(doctorId, exceptionId);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/availability")
  public List<SlotResponse> availability(
      @PathVariable UUID doctorId,
      @RequestParam LocalDate from,
      @RequestParam LocalDate to,
      @RequestParam(defaultValue = "500") int limit) {
    return availabilityService.availability(doctorId, from, to, limit).stream()
        .map(mapper::toResponse)
        .toList();
  }

  @PostMapping("/appointments/{appointmentId}/confirm")
  public SlotResponse confirm(@PathVariable UUID doctorId, @PathVariable UUID appointmentId) {
    requireDoctor(doctorId, reservationService.findByAppointment(appointmentId));
    return mapper.toResponse(reservationService.confirm(appointmentId));
  }

  @PostMapping("/appointments/{appointmentId}/release")
  public ReleaseResponse release(@PathVariable UUID doctorId, @PathVariable UUID appointmentId) {
    requireDoctor(doctorId, reservationService.findByAppointment(appointmentId));
    return new ReleaseResponse(
        reservationService.release(
            new com.telemed.schedule.application.dto.command.ReleaseSlotCommand(appointmentId)));
  }

  private static void requireDoctor(
      UUID doctorId, com.telemed.schedule.application.dto.view.SlotView slot) {
    if (!doctorId.equals(slot.doctorId())) {
      throw new org.springframework.security.access.AccessDeniedException(
          "Appointment does not belong to this doctor");
    }
  }
}
