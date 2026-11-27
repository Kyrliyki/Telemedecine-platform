package com.telemed.auth.presentation.rest.v1.dto.response;

import java.util.List;

public record AdminUserPageResponse(
    List<AdminUserResponse> content, int page, int size, long totalElements, int totalPages) {}
