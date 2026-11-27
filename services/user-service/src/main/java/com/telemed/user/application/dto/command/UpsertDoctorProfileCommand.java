package com.telemed.user.application.dto.command;

public record UpsertDoctorProfileCommand(
    String specialization,
    String bio,
    int yearsOfExperience,
    String licenseNumber,
    boolean acceptingPatients) {}
