package com.telemed.emr.presentation.rest.v1.dto.response;

import java.util.List;

public record MedicalRecordPageResponse(
    List<MedicalRecordResponse> content, long totalElements, int page, int size, int totalPages) {}
