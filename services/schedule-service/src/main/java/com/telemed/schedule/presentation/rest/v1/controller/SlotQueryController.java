package com.telemed.schedule.presentation.rest.v1.controller;

import com.telemed.schedule.application.service.SlotReservationService;
import com.telemed.schedule.presentation.rest.v1.dto.response.SlotResponse;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule")
public class SlotQueryController {

  private final SlotReservationService service;
  private final ScheduleWebMapper mapper;

  public SlotQueryController(SlotReservationService service, ScheduleWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping("/slots/{slotId}")
  public SlotResponse byId(@PathVariable UUID slotId) {
    return mapper.toResponse(service.findById(slotId));
  }

  @GetMapping("/appointments/{appointmentId}/slot")
  public SlotResponse byAppointment(@PathVariable UUID appointmentId) {
    return mapper.toResponse(service.findByAppointment(appointmentId));
  }
}
