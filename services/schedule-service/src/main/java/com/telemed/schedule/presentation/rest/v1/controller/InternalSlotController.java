package com.telemed.schedule.presentation.rest.v1.controller;

import com.telemed.schedule.application.service.SlotReservationService;
import com.telemed.schedule.presentation.rest.v1.dto.request.ReleaseSlotRequest;
import com.telemed.schedule.presentation.rest.v1.dto.request.ReserveSlotRequest;
import com.telemed.schedule.presentation.rest.v1.dto.response.ReleaseResponse;
import com.telemed.schedule.presentation.rest.v1.dto.response.SlotResponse;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/schedule/slots")
@Hidden
public class InternalSlotController {

  private final SlotReservationService service;
  private final ScheduleWebMapper mapper;

  public InternalSlotController(SlotReservationService service, ScheduleWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping({"/hold", "/reserve"})
  public ResponseEntity<SlotResponse> hold(@Valid @RequestBody ReserveSlotRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.hold(mapper.toCommand(request))));
  }

  @PostMapping("/confirm")
  public SlotResponse confirm(@Valid @RequestBody ReleaseSlotRequest request) {
    return mapper.toResponse(service.confirm(request.appointmentId()));
  }

  @PostMapping("/release")
  public ReleaseResponse release(@Valid @RequestBody ReleaseSlotRequest request) {
    return new ReleaseResponse(service.release(mapper.toCommand(request)));
  }
}
