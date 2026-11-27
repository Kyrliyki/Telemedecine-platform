package com.telemed.schedule.infrastructure.persistence.adapter;

import com.telemed.schedule.application.port.output.ScheduleExceptionRepository;
import com.telemed.schedule.domain.model.ScheduleException;
import com.telemed.schedule.infrastructure.persistence.mapper.ScheduleExceptionEntityMapper;
import com.telemed.schedule.infrastructure.persistence.repository.JpaScheduleExceptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ScheduleExceptionRepositoryAdapter implements ScheduleExceptionRepository {

  private final JpaScheduleExceptionRepository jpa;
  private final ScheduleExceptionEntityMapper mapper;

  public ScheduleExceptionRepositoryAdapter(
      JpaScheduleExceptionRepository jpa, ScheduleExceptionEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public ScheduleException save(ScheduleException exception) {
    return mapper.toDomain(jpa.save(mapper.toEntity(exception)));
  }

  @Override
  public Optional<ScheduleException> findById(UUID id) {
    return jpa.findById(id).map(mapper::toDomain);
  }

  @Override
  public List<ScheduleException> findAllByDoctorId(UUID doctorId) {
    return jpa.findAllByDoctorIdOrderByStartsAtAsc(doctorId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public List<ScheduleException> findOverlapping(UUID doctorId, Instant from, Instant to) {
    return jpa.findOverlapping(doctorId, from, to).stream().map(mapper::toDomain).toList();
  }

  @Override
  public void deleteById(UUID id) {
    jpa.deleteById(id);
  }

  @Override
  public void deleteAllByDoctorId(UUID doctorId) {
    jpa.deleteAllByDoctorId(doctorId);
  }
}
