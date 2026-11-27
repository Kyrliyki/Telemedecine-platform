package com.telemed.user.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @Size(max = 100, message = "firstName must be at most 100 characters") String firstName,
    @Size(max = 100, message = "lastName must be at most 100 characters") String lastName) {}
