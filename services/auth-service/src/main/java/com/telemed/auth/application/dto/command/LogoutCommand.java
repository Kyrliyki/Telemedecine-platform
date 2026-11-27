package com.telemed.auth.application.dto.command;

import java.time.Instant;

public record LogoutCommand(
    String rawRefreshToken, String accessTokenJti, Instant accessTokenExp) {}
