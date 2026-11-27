package com.telemed.auth.presentation.rest.v1.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Set;

public record CreateUserByAdminRequest(
    @NotBlank @Email String email,
    @NotEmpty Set<String> roles,
    @Valid AdminProfileRequest profile) {
  public record AdminProfileRequest(
      String firstName,
      String lastName,
      String middleName,
      String specialization,
      String licenseNumber) {}
}
