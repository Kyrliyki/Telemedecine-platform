package com.telemed.emr.application.dto.command;

public record UpdateMedicalRecordCommand(
    String chiefComplaint,
    String diagnosisCode,
    String diagnosisText,
    String notes,
    String treatmentPlan) {}
