package com.telemed.schedule.presentation.rest.v1.controller;

import com.telemed.common.security.jwt.JwtClaimsExtractor;
import com.telemed.schedule.application.service.ScheduleExceptionService;
import com.telemed.schedule.presentation.rest.v1.dto.request.CreateExceptionRequest;
import com.telemed.schedule.presentation.rest.v1.dto.response.ScheduleExceptionResponse;
import com.telemed.schedule.presentation.rest.v1.mapper.ScheduleWebMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/schedule/exceptions")
@PreAuthorize("hasRole('DOCTOR')")
public class ScheduleExceptionController {

  private final ScheduleExceptionService service;
  private final ScheduleWebMapper mapper;

  public ScheduleExceptionController(ScheduleExceptionService service, ScheduleWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping
  public List<ScheduleExceptionResponse> list(@AuthenticationPrincipal Jwt jwt) {
    return service.list(doctorId(jwt)).stream().map(mapper::toResponse).toList();
  }

  @PostMapping
  public ResponseEntity<ScheduleExceptionResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateExceptionRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.create(doctorId(jwt), mapper.toCommand(request))));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    service.delete(doctorId(jwt), id);
    return ResponseEntity.noContent().build();
  }

  private static UUID doctorId(Jwt jwt) {
    return JwtClaimsExtractor.userId(jwt)
        .orElseThrow(() -> new AccessDeniedException("JWT has no valid subject"));
  }
}
