package com.telemed.emr.presentation.rest.v1.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record MedicalRecordResponse(
    UUID id,
    UUID patientId,
    UUID doctorId,
    UUID appointmentId,
    String status,
    String chiefComplaint,
    String diagnosisCode,
    String diagnosisText,
    String notes,
    String treatmentPlan,
    Instant createdAt,
    Instant updatedAt,
    Instant finalizedAt,
    long version,
    List<PrescriptionResponse> prescriptions,
    VitalSignsResponse vitalSigns,
    List<AttachmentResponse> attachments) {}
