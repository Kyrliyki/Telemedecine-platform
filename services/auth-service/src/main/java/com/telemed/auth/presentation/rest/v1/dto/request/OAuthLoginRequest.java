package com.telemed.auth.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OAuthLoginRequest(@NotBlank String authorizationCode, String redirectUri) {}
