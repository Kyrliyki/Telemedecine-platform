package com.telemed.schedule.application.port.output;

import com.telemed.schedule.domain.model.TimeSlot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotRepository {

  TimeSlot save(TimeSlot slot);

  Optional<TimeSlot> findById(UUID id);

  Optional<TimeSlot> findByDoctorIdAndStartsAt(UUID doctorId, Instant startsAt);

  Optional<TimeSlot> findByAppointmentId(UUID appointmentId);

  List<TimeSlot> findOverlapping(UUID doctorId, Instant from, Instant to);

  List<TimeSlot> findExpiredHolds(Instant now, int limit);

  List<TimeSlot> findConfirmed(Instant from, int limit);

  int deleteEndedBefore(Instant cutoff);

  void deleteAllByDoctorId(UUID doctorId);
}
