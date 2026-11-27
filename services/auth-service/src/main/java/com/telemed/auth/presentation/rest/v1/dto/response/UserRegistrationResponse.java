package com.telemed.auth.presentation.rest.v1.dto.response;

import java.util.Set;
import java.util.UUID;

public record UserRegistrationResponse(
    UUID userId, String email, Set<String> roles, String status, boolean invitationRequired) {}
