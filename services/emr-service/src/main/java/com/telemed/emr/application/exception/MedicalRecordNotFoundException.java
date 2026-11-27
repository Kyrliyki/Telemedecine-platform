package com.telemed.emr.application.exception;

import java.util.UUID;

public class MedicalRecordNotFoundException extends RuntimeException {

  public MedicalRecordNotFoundException(UUID id) {
    super("Medical record not found: " + id);
  }
}
