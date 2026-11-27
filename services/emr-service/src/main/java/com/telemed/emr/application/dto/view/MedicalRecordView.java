package com.telemed.emr.application.dto.view;

import java.time.Instant;
import java.util.UUID;

public record MedicalRecordView(
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
    java.util.List<PrescriptionView> prescriptions,
    VitalSignsView vitalSigns,
    java.util.List<AttachmentView> attachments) {}
