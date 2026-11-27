package com.telemed.user.presentation.rest.v1.dto.response;

import java.util.List;

public record DoctorPageResponse(
    List<DoctorProfileResponse> content, long totalElements, int page, int size, int totalPages) {}
