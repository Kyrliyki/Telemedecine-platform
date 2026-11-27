package com.telemed.auth.application.dto.view;

import java.util.Set;
import java.util.UUID;

public record UserRegistrationResult(
    UUID userId, String email, Set<String> roles, String status, boolean invitationRequired) {}
