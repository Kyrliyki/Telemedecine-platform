package com.telemed.user.application.dto.query;

public record AdminUserQuery(int page, int size, String email, String status, String role) {}
