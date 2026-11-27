package com.telemed.auth.application.dto.view;

public record AuthView(
    String accessToken, String refreshToken, long accessExpiresInSeconds, UserInfoResult user) {}
