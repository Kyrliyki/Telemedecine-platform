package com.telemed.user.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpsertDoctorProfileRequest(
    @NotBlank(message = "specialization is required")
        @Size(max = 120, message = "specialization must be at most 120 characters")
        String specialization,
    @Size(max = 4000, message = "bio must be at most 4000 characters") String bio,
    @Min(value = 0, message = "yearsOfExperience must be >= 0")
        @Max(value = 80, message = "yearsOfExperience must be <= 80")
        Integer yearsOfExperience,
    @Size(max = 64, message = "licenseNumber must be at most 64 characters") String licenseNumber,
    Boolean acceptingPatients) {}
