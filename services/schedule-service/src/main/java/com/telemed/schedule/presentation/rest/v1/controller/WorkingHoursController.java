package com.telemed.schedule.presentation.rest.v1.controller;

import com.telemed.common.security.jwt.JwtClaimsExtractor;
import com.telemed.schedule.application.service.WorkingHoursService;
import com.telemed.schedule.presentation.rest.v1.dto.request.UpdateWorkingHoursRequest;
import com.telemed.schedule.presentation.rest.v1.dto.response.WorkingHoursResponse;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule/working-hours")
@PreAuthorize("hasRole('DOCTOR')")
public class WorkingHoursController {

  private final WorkingHoursService service;
  private final ScheduleWebMapper mapper;

  public WorkingHoursController(WorkingHoursService service, ScheduleWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping
  public List<WorkingHoursResponse> get(@AuthenticationPrincipal Jwt jwt) {
    return service.get(doctorId(jwt)).stream().map(mapper::toResponse).toList();
  }

  @PutMapping
  public ResponseEntity<List<WorkingHoursResponse>> replace(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateWorkingHoursRequest request) {
    return ResponseEntity.ok(
        service.replace(doctorId(jwt), mapper.toCommand(request)).stream()
            .map(mapper::toResponse)
            .toList());
  }

  private static UUID doctorId(Jwt jwt) {
    return JwtClaimsExtractor.userId(jwt)
        .orElseThrow(() -> new AccessDeniedException("JWT has no valid subject"));
  }
}
