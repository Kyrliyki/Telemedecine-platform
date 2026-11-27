package com.telemed.user.presentation.rest.v1.controller;

import com.telemed.user.application.dto.view.DoctorProfileView;
import com.telemed.user.application.dto.view.UserProfileView;
import com.telemed.user.application.port.input.GetUserProfileUseCase;
import com.telemed.user.presentation.rest.v1.dto.response.InternalUserResponse;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/users")
@Hidden
@ConditionalOnProperty(
    prefix = "security.internal",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class InternalUserController {

  private final GetUserProfileUseCase profileUseCase;

  public InternalUserController(GetUserProfileUseCase profileUseCase) {
    this.profileUseCase = profileUseCase;
  }

  @GetMapping("/{userId}")
  public InternalUserResponse get(@PathVariable UUID userId) {
    return toResponse(profileUseCase.getProfile(userId));
  }

  private static InternalUserResponse toResponse(UserProfileView view) {
    DoctorProfileView doctor = view.doctorProfile();
    return new InternalUserResponse(
        view.id(),
        view.email(),
        view.firstName(),
        view.lastName(),
        displayName(view.firstName(), view.lastName(), view.email()),
        view.roles().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.toSet()),
        view.status().name(),
        view.updatedAt(),
        doctor == null
            ? null
            : new InternalUserResponse.InternalDoctorProfile(
                doctor.specialization(),
                doctor.licenseNumber(),
                doctor.acceptingPatients(),
                doctor.verificationStatus(),
                doctor.verifiedAt(),
                doctor.verifiedBy()));
  }

  private static String displayName(String firstName, String lastName, String email) {
    String name =
        ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).trim();
    return name.isEmpty() ? email : name;
  }
}
