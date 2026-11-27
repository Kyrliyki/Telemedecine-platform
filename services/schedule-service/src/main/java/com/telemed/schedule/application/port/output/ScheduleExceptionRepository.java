package com.telemed.schedule.application.port.output;

import com.telemed.schedule.domain.model.ScheduleException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScheduleExceptionRepository {

  ScheduleException save(ScheduleException exception);

  Optional<ScheduleException> findById(UUID id);

  List<ScheduleException> findAllByDoctorId(UUID doctorId);

  List<ScheduleException> findOverlapping(UUID doctorId, Instant from, Instant to);

  void deleteById(UUID id);

  void deleteAllByDoctorId(UUID doctorId);
}
