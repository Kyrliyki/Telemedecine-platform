package com.telemed.schedule.infrastructure.persistence.adapter;

import com.telemed.schedule.application.port.output.TimeSlotRepository;
import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.infrastructure.persistence.mapper.TimeSlotEntityMapper;
import com.telemed.schedule.infrastructure.persistence.repository.JpaTimeSlotRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TimeSlotRepositoryAdapter implements TimeSlotRepository {

  private final JpaTimeSlotRepository jpa;
  private final TimeSlotEntityMapper mapper;

  public TimeSlotRepositoryAdapter(JpaTimeSlotRepository jpa, TimeSlotEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public TimeSlot save(TimeSlot slot) {
    return mapper.toDomain(jpa.save(mapper.toEntity(slot)));
  }

  @Override
  public Optional<TimeSlot> findById(UUID id) {
    return jpa.findById(id).map(mapper::toDomain);
  }

  @Override
  public Optional<TimeSlot> findByDoctorIdAndStartsAt(UUID doctorId, Instant startsAt) {
    return jpa.findByDoctorIdAndStartsAt(doctorId, startsAt).map(mapper::toDomain);
  }

  @Override
  public Optional<TimeSlot> findByAppointmentId(UUID appointmentId) {
    return jpa.findByAppointmentId(appointmentId).map(mapper::toDomain);
  }

  @Override
  public List<TimeSlot> findOverlapping(UUID doctorId, Instant from, Instant to) {
    return jpa.findOverlapping(doctorId, from, to).stream().map(mapper::toDomain).toList();
  }

  @Override
  public List<TimeSlot> findExpiredHolds(Instant now, int limit) {
    return jpa
        .findAllByStatusAndHoldExpiresAtBefore(
            com.telemed.schedule.domain.valueobject.SlotStatus.HELD,
            now,
            org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)))
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public int deleteEndedBefore(Instant cutoff) {
    return jpa.deleteEndedBefore(cutoff);
  }

  @Override
  public List<TimeSlot> findConfirmed(Instant from, int limit) {
    return jpa
        .findAllByStatusAndStartsAtAfter(
            com.telemed.schedule.domain.valueobject.SlotStatus.CONFIRMED,
            from,
            org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)))
        .stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public void deleteAllByDoctorId(UUID doctorId) {
    jpa.deleteAllByDoctorId(doctorId);
  }
}
