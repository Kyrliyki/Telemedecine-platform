package com.telemed.auth.application.dto.view;

import java.util.List;

public record AuthAuditPageView(
    List<AuthAuditView> content, long totalElements, int page, int size, int totalPages) {}
