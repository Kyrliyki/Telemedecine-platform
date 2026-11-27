package com.telemed.schedule.application.service;

import com.telemed.schedule.infrastructure.client.UserDirectoryClient;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class UserValidationService {

  private final UserDirectoryClient client;

  public UserValidationService(UserDirectoryClient client) {
    this.client = client;
  }

  public void requireActiveDoctor(UUID doctorId) {
    if (!client.enabled() || doctorId == null) {
      return;
    }
    UserDirectoryClient.InternalUser user = load(doctorId, "Doctor");
    if (!"ACTIVE".equalsIgnoreCase(user.status())) {
      throw new AccessDeniedException("Doctor account is not active");
    }
    if (user.roles() == null || !user.roles().contains("DOCTOR")) {
      throw new AccessDeniedException("User does not have the DOCTOR role");
    }
    if (user.doctorProfile() == null) {
      throw new AccessDeniedException("Doctor profile is missing");
    }
    if (!"VERIFIED".equalsIgnoreCase(user.doctorProfile().verificationStatus())) {
      throw new AccessDeniedException("Doctor profile is not verified");
    }
  }

  public void requireActivePatient(UUID patientId) {
    if (!client.enabled() || patientId == null) {
      return;
    }
    UserDirectoryClient.InternalUser user = load(patientId, "Patient");
    if (!"ACTIVE".equalsIgnoreCase(user.status())) {
      throw new AccessDeniedException("Patient account is not active");
    }
    if (user.roles() == null || !user.roles().contains("PATIENT")) {
      throw new AccessDeniedException("User does not have the PATIENT role");
    }
  }

  private UserDirectoryClient.InternalUser load(UUID userId, String label) {
    return client
        .find(userId)
        .orElseThrow(() -> new AccessDeniedException(label + " account was not found"));
  }
}
