package com.telemed.user.application.exception;

import java.util.UUID;

public class DoctorProfileNotFoundException extends RuntimeException {

  public DoctorProfileNotFoundException(UUID userId) {
    super("Doctor profile not found: " + userId);
  }
}
