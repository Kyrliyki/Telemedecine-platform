package com.telemed.user.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectDoctorRequest(@NotBlank @Size(max = 500) String reason) {}
