package com.telemed.user.presentation.rest.v1.controller;

import com.telemed.common.security.jwt.JwtClaimsExtractor;
import com.telemed.user.application.dto.command.UpsertDoctorProfileCommand;
import com.telemed.user.application.port.input.GetDoctorProfileUseCase;
import com.telemed.user.application.port.input.GetUserProfileUseCase;
import com.telemed.user.application.port.input.UpdateUserProfileUseCase;
import com.telemed.user.application.port.input.UpsertDoctorProfileUseCase;
import com.telemed.user.presentation.rest.v1.dto.request.UpdateProfileRequest;
import com.telemed.user.presentation.rest.v1.dto.request.UpsertDoctorProfileRequest;
import com.telemed.user.presentation.rest.v1.dto.response.DoctorProfileResponse;
import com.telemed.user.presentation.rest.v1.dto.response.UserProfileResponse;
import com.telemed.user.presentation.rest.v1.mapper.UserWebMapper;
import jakarta.validation.Valid;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

  private final GetUserProfileUseCase getUserProfileUseCase;
  private final UpdateUserProfileUseCase updateUserProfileUseCase;
  private final GetDoctorProfileUseCase getDoctorProfileUseCase;
  private final UpsertDoctorProfileUseCase upsertDoctorProfileUseCase;
  private final UserWebMapper mapper;

  public UserController(
      GetUserProfileUseCase getUserProfileUseCase,
      UpdateUserProfileUseCase updateUserProfileUseCase,
      GetDoctorProfileUseCase getDoctorProfileUseCase,
      UpsertDoctorProfileUseCase upsertDoctorProfileUseCase,
      UserWebMapper mapper) {
    this.getUserProfileUseCase = getUserProfileUseCase;
    this.updateUserProfileUseCase = updateUserProfileUseCase;
    this.getDoctorProfileUseCase = getDoctorProfileUseCase;
    this.upsertDoctorProfileUseCase = upsertDoctorProfileUseCase;
    this.mapper = mapper;
  }

  @GetMapping("/me")
  public UserProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
    UUID userId = currentUserId(jwt);
    String email =
        JwtClaimsExtractor.email(jwt)
            .orElseThrow(() -> new AccessDeniedException("JWT has no email claim"));
    return mapper.toResponse(
        getUserProfileUseCase.getOrCreateFromToken(
            userId, email, new HashSet<>(JwtClaimsExtractor.roles(jwt))));
  }

  @PatchMapping("/me")
  public UserProfileResponse updateMe(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpdateProfileRequest request) {
    return mapper.toResponse(
        updateUserProfileUseCase.updateProfile(currentUserId(jwt), mapper.toCommand(request)));
  }

  @GetMapping("/{userId}")
  public UserProfileResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID userId) {
    UUID currentUserId = currentUserId(jwt);
    if (!currentUserId.equals(userId) && !hasAnyRole(jwt, "ADMIN", "DOCTOR")) {
      throw new AccessDeniedException("Only self, ADMIN or DOCTOR can view this profile");
    }
    return mapper.toResponse(getUserProfileUseCase.getProfile(userId));
  }

  @GetMapping("/me/doctor-profile")
  @PreAuthorize("hasRole('DOCTOR')")
  public DoctorProfileResponse myDoctorProfile(@AuthenticationPrincipal Jwt jwt) {
    return mapper.toResponse(getDoctorProfileUseCase.getForOwner(currentUserId(jwt)));
  }

  @PutMapping("/me/doctor-profile")
  @PreAuthorize("hasRole('DOCTOR')")
  public ResponseEntity<DoctorProfileResponse> upsertMyDoctorProfile(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody UpsertDoctorProfileRequest request) {
    UpsertDoctorProfileCommand command = mapper.toCommand(request);
    return ResponseEntity.ok(
        mapper.toResponse(upsertDoctorProfileUseCase.upsert(currentUserId(jwt), command)));
  }

  private static UUID currentUserId(Jwt jwt) {
    return JwtClaimsExtractor.userId(jwt)
        .orElseThrow(() -> new AccessDeniedException("JWT has no valid subject"));
  }

  private static boolean hasAnyRole(Jwt jwt, String... roles) {
    var granted = JwtClaimsExtractor.roles(jwt);
    for (String role : roles) {
      if (granted.contains(role)) {
        return true;
      }
    }
    return false;
  }
}
