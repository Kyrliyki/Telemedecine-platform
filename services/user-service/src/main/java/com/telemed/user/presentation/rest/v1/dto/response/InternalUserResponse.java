package com.telemed.user.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record InternalUserResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    String displayName,
    Set<String> roles,
    String status,
    Instant updatedAt,
    InternalDoctorProfile doctorProfile) {

  public record InternalDoctorProfile(
      String specialization,
      String licenseNumber,
      boolean acceptingPatients,
      String verificationStatus,
      Instant verifiedAt,
      UUID verifiedBy) {}
}
