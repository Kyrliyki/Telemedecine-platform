package com.telemed.emr.presentation.rest.v1.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateMedicalRecordRequest(
    @NotNull UUID patientId,
    UUID appointmentId,
    UUID doctorId,
    @Size(max = 1000) String chiefComplaint,
    @Size(max = 32) String diagnosisCode,
    @Size(max = 1000) String diagnosisText,
    String notes,
    String treatmentPlan) {}
