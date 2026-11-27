package com.telemed.user.presentation.rest.v1.controller;

import com.telemed.user.application.dto.command.UpsertDoctorProfileCommand;
import com.telemed.user.application.service.DoctorProfileService;
import com.telemed.user.presentation.rest.v1.dto.request.RejectDoctorRequest;
import com.telemed.user.presentation.rest.v1.dto.request.UpsertDoctorProfileRequest;
import com.telemed.user.presentation.rest.v1.dto.response.DoctorPageResponse;
import com.telemed.user.presentation.rest.v1.dto.response.DoctorProfileResponse;
import com.telemed.user.presentation.rest.v1.mapper.UserWebMapper;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users/doctors")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDoctorController {

  private final DoctorProfileService service;
  private final UserWebMapper mapper;

  public AdminDoctorController(DoctorProfileService service, UserWebMapper mapper) {
    this.service = service;
    this.mapper = mapper;
  }

  @GetMapping
  public DoctorPageResponse list(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(required = false) String verificationStatus,
      @RequestParam(required = false) String specialization) {
    return mapper.toDoctorPageResponse(
        service.findAllForAdmin(verificationStatus, specialization, page, size));
  }

  @GetMapping("/{userId}")
  public DoctorProfileResponse get(@PathVariable UUID userId) {
    return mapper.toResponse(service.getInternal(userId));
  }

  @PostMapping("/{userId}/approve")
  public DoctorProfileResponse approve(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    return mapper.toResponse(service.approve(userId, actorId(jwt)));
  }

  @PostMapping("/{userId}/reject")
  public DoctorProfileResponse reject(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID userId,
      @Valid @RequestBody RejectDoctorRequest request) {
    return mapper.toResponse(service.reject(userId, actorId(jwt), request.reason()));
  }

  @PutMapping("/{userId}")
  public DoctorProfileResponse update(
      @PathVariable UUID userId, @Valid @RequestBody UpsertDoctorProfileRequest request) {
    UpsertDoctorProfileCommand command = mapper.toCommand(request);
    return mapper.toResponse(service.updateByAdmin(userId, command));
  }

  private static UUID actorId(Jwt jwt) {
    if (jwt == null || jwt.getSubject() == null) {
      throw new AccessDeniedException("Authenticated JWT is required");
    }
    return UUID.fromString(jwt.getSubject());
  }
}
