package com.telemed.schedule.application.dto.command;

import java.time.Instant;
import java.util.UUID;

public record ReserveSlotCommand(
    UUID doctorId, Instant startsAt, UUID appointmentId, UUID patientId) {

  public ReserveSlotCommand(UUID doctorId, Instant startsAt, UUID appointmentId) {
    this(doctorId, startsAt, appointmentId, null);
  }
}
