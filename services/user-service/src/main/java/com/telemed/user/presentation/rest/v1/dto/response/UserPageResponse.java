package com.telemed.user.presentation.rest.v1.dto.response;

import java.util.List;

public record UserPageResponse(
    List<UserProfileResponse> content, long totalElements, int page, int size, int totalPages) {}
