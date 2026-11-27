package com.telemed.emr.presentation.rest.v1.controller;

import com.telemed.common.security.jwt.JwtClaimsExtractor;
import com.telemed.emr.application.dto.query.MedicalRecordQuery;
import com.telemed.emr.application.dto.view.MedicalRecordRevisionView;
import com.telemed.emr.application.service.MedicalRecordService;
import com.telemed.emr.presentation.rest.v1.dto.request.AddAttachmentRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.CreateMedicalRecordRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.ReplacePrescriptionsRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.UpdateMedicalRecordRequest;
import com.telemed.emr.presentation.rest.v1.dto.request.UpsertVitalSignsRequest;
import com.telemed.emr.presentation.rest.v1.dto.response.MedicalRecordPageResponse;
import com.telemed.emr.presentation.rest.v1.dto.response.MedicalRecordResponse;
import com.telemed.emr.presentation.rest.v1.mapper.EmrWebMapper;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/emr")
public class MedicalRecordController {

  private final MedicalRecordService service;
  private final EmrWebMapper mapper;

  public MedicalRecordController(MedicalRecordService service, EmrWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @PostMapping("/records")
  @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
  public ResponseEntity<MedicalRecordResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateMedicalRecordRequest request) {
    Actor actor = actor(jwt);
    UUID doctorId = actor.admin() ? request.doctorId() : actor.id();
    if (doctorId == null) {
      throw new AccessDeniedException("doctorId is required for administrators");
    }
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(mapper.toResponse(service.create(doctorId, mapper.toCommand(request))));
  }

  @GetMapping("/records/{id}")
  public MedicalRecordResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    Actor actor = actor(jwt);
    return mapper.toResponse(service.get(id, actor.id(), actor.roles()));
  }

  @GetMapping("/records/{id}/revisions")
  public List<MedicalRecordRevisionView> revisions(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    Actor actor = actor(jwt);
    return service.revisions(id, actor.id(), actor.roles());
  }

  @GetMapping("/records/{id}/revisions/{revisionNumber}")
  public MedicalRecordRevisionView revision(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable int revisionNumber) {
    Actor actor = actor(jwt);
    return service.revision(id, revisionNumber, actor.id(), actor.roles());
  }

  @PatchMapping("/records/{id}")
  public MedicalRecordResponse update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateMedicalRecordRequest request) {
    Actor actor = actor(jwt);
    return mapper.toResponse(
        service.update(id, mapper.toCommand(request), actor.id(), actor.admin()));
  }

  @PostMapping("/records/{id}/finalize")
  public MedicalRecordResponse finalizeRecord(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    Actor actor = actor(jwt);
    return mapper.toResponse(service.finalizeRecord(id, actor.id(), actor.admin()));
  }

  @PostMapping("/records/{id}/amend")
  public MedicalRecordResponse amend(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    Actor actor = actor(jwt);
    return mapper.toResponse(service.amend(id, actor.id(), actor.admin()));
  }

  @PutMapping("/records/{id}/prescriptions")
  public MedicalRecordResponse prescriptions(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody ReplacePrescriptionsRequest request) {
    Actor actor = actor(jwt);
    return mapper.toResponse(
        service.replacePrescriptions(id, mapper.toCommand(request), actor.id(), actor.admin()));
  }

  @PutMapping("/records/{id}/vitals")
  public MedicalRecordResponse vitals(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody UpsertVitalSignsRequest request) {
    Actor actor = actor(jwt);
    return mapper.toResponse(
        service.upsertVitalSigns(id, mapper.toCommand(request), actor.id(), actor.admin()));
  }

  @PostMapping("/records/{id}/attachments")
  public MedicalRecordResponse attachment(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody AddAttachmentRequest request) {
    Actor actor = actor(jwt);
    return mapper.toResponse(
        service.addAttachment(id, mapper.toCommand(request), actor.id(), actor.admin()));
  }

  @GetMapping("/patients/{patientId}/records")
  public MedicalRecordPageResponse byPatient(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID patientId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status) {
    Actor actor = actor(jwt);
    return mapper.toPageResponse(
        service.byPatient(
            patientId, new MedicalRecordQuery(page, size, status), actor.id(), actor.roles()));
  }

  @GetMapping("/me/records")
  public MedicalRecordPageResponse mine(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String view) {
    Actor actor = actor(jwt);
    return mapper.toPageResponse(
        service.mine(actor.id(), actor.roles(), new MedicalRecordQuery(page, size, status), view));
  }

  private static Actor actor(Jwt jwt) {
    UUID id =
        JwtClaimsExtractor.userId(jwt)
            .orElseThrow(() -> new AccessDeniedException("JWT has no valid subject"));
    Set<String> roles = Set.copyOf(JwtClaimsExtractor.roles(jwt));
    return new Actor(id, roles, roles.contains("ADMIN"), roles.contains("DOCTOR"));
  }

  private record Actor(UUID id, Set<String> roles, boolean admin, boolean doctor) {}
}
