package com.telemed.emr.application.dto.command;

import java.util.UUID;

public record CreateMedicalRecordCommand(
    UUID patientId,
    UUID appointmentId,
    String chiefComplaint,
    String diagnosisCode,
    String diagnosisText,
    String notes,
    String treatmentPlan) {}
