package com.telemed.user.application.dto.view;

import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserProfileView(
    UUID id,
    String email,
    String firstName,
    String lastName,
    Set<UserRole> roles,
    UserStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant lastSeenAt,
    long version,
    DoctorProfileView doctorProfile) {}
