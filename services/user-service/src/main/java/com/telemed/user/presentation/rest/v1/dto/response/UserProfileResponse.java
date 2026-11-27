package com.telemed.user.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    Set<String> roles,
    String status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastSeenAt,
    long version,
    DoctorProfileResponse doctorProfile) {}
