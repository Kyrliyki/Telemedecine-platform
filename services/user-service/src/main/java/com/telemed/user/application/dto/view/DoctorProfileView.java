package com.telemed.user.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record DoctorProfileView(
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
