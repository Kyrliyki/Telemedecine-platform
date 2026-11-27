package com.telemed.emr.presentation.rest.v1.controller;

import com.telemed.common.security.jwt.JwtClaimsExtractor;
import com.telemed.emr.application.dto.view.EmrAuditView;
import com.telemed.emr.application.service.MedicalRecordService;
import com.telemed.emr.presentation.rest.v1.dto.response.MedicalRecordResponse;
import com.telemed.emr.presentation.rest.v1.mapper.EmrWebMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/emr")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEmrController {

  private final MedicalRecordService service;
  private final EmrWebMapper mapper;

  public AdminEmrController(MedicalRecordService service, EmrWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping("/records/{id}/legal-hold")
  public MedicalRecordResponse legalHold(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "true") boolean enabled) {
    return mapper.toResponse(service.setLegalHold(id, enabled, actor(jwt), true));
  }

  @PostMapping("/records/{id}/retention")
  public MedicalRecordResponse retention(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @RequestParam Instant until) {
    return mapper.toResponse(service.setRetention(id, until, actor(jwt), true));
  }

  @GetMapping("/patients/{patientId}/export")
  public List<MedicalRecordResponse> export(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID patientId) {
    actor(jwt);
    return service.exportByPatient(patientId, true).stream().map(mapper::toResponse).toList();
  }

  @PostMapping("/purge-expired")
  public PurgeResponse purge(@RequestParam(defaultValue = "200") int limit) {
    return new PurgeResponse(service.purgeExpired(Math.max(1, Math.min(limit, 1000))));
  }

  @GetMapping("/records/{id}/audit")
  public List<EmrAuditView> audit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    actor(jwt);
    return service.auditForRecord(id, true);
  }

  private static UUID actor(Jwt jwt) {
    return JwtClaimsExtractor.userId(jwt)
        .orElseThrow(() -> new AccessDeniedException("JWT has no valid subject"));
  }

  public record PurgeResponse(int purged) {}
}
