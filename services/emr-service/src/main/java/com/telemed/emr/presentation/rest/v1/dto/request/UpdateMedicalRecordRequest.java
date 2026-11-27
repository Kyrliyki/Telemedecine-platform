package com.telemed.emr.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateMedicalRecordRequest(
    @Size(max = 1000) String chiefComplaint,
    @Size(max = 32) String diagnosisCode,
    @Size(max = 1000) String diagnosisText,
    String notes,
    String treatmentPlan) {}
