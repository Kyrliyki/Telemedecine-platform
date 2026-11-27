package com.telemed.user.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.UUID;

public record DoctorProfileResponse(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    String specialization,
    String bio,
    int yearsOfExperience,
    String licenseNumber,
    boolean acceptingPatients,
    Instant createdAt,
    Instant updatedAt,
    long version,
    String verificationStatus,
    Instant verifiedAt,
    UUID verifiedBy,
    String rejectionReason) {}
