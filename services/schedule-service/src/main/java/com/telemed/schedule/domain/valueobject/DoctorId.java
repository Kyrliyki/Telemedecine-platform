package com.telemed.schedule.domain.valueobject;

import java.util.Objects;
import java.util.UUID;

public record DoctorId(UUID value) {

  public DoctorId {
    Objects.requireNonNull(value, "doctorId required");
  }

  public static DoctorId of(UUID value) {
    return new DoctorId(value);
  }
}
