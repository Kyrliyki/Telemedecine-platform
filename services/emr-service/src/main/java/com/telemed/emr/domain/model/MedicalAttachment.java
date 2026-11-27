package com.telemed.emr.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MedicalAttachment(
    UUID id, UUID recordId, UUID fileId, String description, Instant createdAt) {

  public MedicalAttachment {
    Objects.requireNonNull(id, "id required");
    Objects.requireNonNull(recordId, "recordId required");
    Objects.requireNonNull(fileId, "fileId required");
    description = description == null ? null : description.trim();
    Objects.requireNonNull(createdAt, "createdAt required");
  }

  public static MedicalAttachment create(
      UUID recordId, UUID fileId, String description, Instant now) {
    return new MedicalAttachment(UUID.randomUUID(), recordId, fileId, description, now);
  }
}
