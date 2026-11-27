package com.telemed.schedule.presentation.rest.v1.controller;

import com.telemed.schedule.application.service.AvailabilityService;
import com.telemed.schedule.presentation.rest.v1.dto.request.BulkAvailabilityRequest;
import com.telemed.schedule.presentation.rest.v1.dto.response.BulkAvailabilityResponse;
import com.telemed.schedule.presentation.rest.v1.dto.response.SlotResponse;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule")
public class AvailabilityController {

  private final AvailabilityService service;
  private final ScheduleWebMapper mapper;

  public AvailabilityController(AvailabilityService service, ScheduleWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping("/doctors/{doctorId}/availability")
  public ResponseEntity<List<SlotResponse>> availability(
      @PathVariable UUID doctorId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") int limit,
      @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
    List<SlotResponse> slots =
        service.availability(doctorId, from, to, limit).stream().map(mapper::toResponse).toList();
    String etag = "\"" + Integer.toHexString(slots.hashCode()) + "\"";
    if (etag.equals(ifNoneMatch)) {
      return ResponseEntity.status(304).eTag(etag).build();
    }
    return ResponseEntity.ok()
        .eTag(etag)
        .cacheControl(CacheControl.maxAge(java.time.Duration.ofSeconds(15)).cachePrivate())
        .body(slots);
  }

  @GetMapping("/doctors/{doctorId}/next-available")
  public ResponseEntity<SlotResponse> nextAvailable(
      @PathVariable UUID doctorId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
    var slot = service.nextAvailable(doctorId, from);
    return slot == null
        ? ResponseEntity.noContent().build()
        : ResponseEntity.ok(mapper.toResponse(slot));
  }

  @PostMapping("/doctors/availability")
  public BulkAvailabilityResponse bulk(@Valid @RequestBody BulkAvailabilityRequest request) {
    Map<UUID, List<SlotResponse>> result = new LinkedHashMap<>();
    for (UUID doctorId : request.doctorIds()) {
      result.put(
          doctorId,
          service.availability(doctorId, request.from(), request.to(), request.limit()).stream()
              .map(mapper::toResponse)
              .toList());
    }
    return new BulkAvailabilityResponse(result);
  }
}
