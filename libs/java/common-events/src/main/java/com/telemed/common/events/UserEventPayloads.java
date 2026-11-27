package com.telemed.common.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payloads published by user-service after profile or doctor-verification
 * changes.
 *
 * <p>All fields are deliberately flat or nullable records so that Python
 * consumers can validate the same payload with Pydantic without sharing Java
 * classes.
 */
public final class UserEventPayloads {

    private UserEventPayloads() {
    }

    public record ProfileUpdatedPayload(
            UUID userId,
            String email,
            String firstName,
            String lastName,
            String displayName,
            List<String> roles,
            String status,
            Instant updatedAt,
            DoctorProfileSnapshot doctorProfile
    ) {
        public ProfileUpdatedPayload {
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record DoctorProfileSnapshot(
            String specialization,
            String bio,
            int yearsOfExperience,
            String licenseNumber,
            boolean acceptingPatients,
            String verificationStatus,
            Instant verifiedAt,
            UUID verifiedBy
    ) {
    }

    public record DoctorVerifiedPayload(
            UUID userId,
            String email,
            String verificationStatus,
            UUID verifiedBy,
            Instant verifiedAt
    ) {
    }

    public record DoctorRejectedPayload(
            UUID userId,
            String email,
            String verificationStatus,
            String reason,
            UUID verifiedBy,
            Instant rejectedAt
    ) {
    }
}
