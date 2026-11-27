package com.telemed.schedule.infrastructure.persistence.adapter;

import com.telemed.schedule.application.port.output.WorkingHoursRepository;
import com.telemed.schedule.domain.model.WorkingHours;
import com.telemed.schedule.infrastructure.persistence.mapper.WorkingHoursEntityMapper;
import com.telemed.schedule.infrastructure.persistence.repository.JpaWorkingHoursRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WorkingHoursRepositoryAdapter implements WorkingHoursRepository {

  private final JpaWorkingHoursRepository jpa;
  private final WorkingHoursEntityMapper mapper;

  public WorkingHoursRepositoryAdapter(
      JpaWorkingHoursRepository jpa, WorkingHoursEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public List<WorkingHours> findAllByDoctorId(UUID doctorId) {
    return jpa.findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public List<WorkingHours> saveAll(List<WorkingHours> entries) {
    return jpa.saveAll(entries.stream().map(mapper::toEntity).toList()).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  public void deleteAllByDoctorId(UUID doctorId) {
    jpa.deleteAllByDoctorId(doctorId);
  }

  @Override
  public void closeOpenBefore(
      UUID doctorId, java.time.LocalDate fromDate, java.time.LocalDate endDate) {
    jpa.closeOpenBefore(doctorId, fromDate, endDate);
  }

  @Override
  public int deleteFutureFrom(UUID doctorId, java.time.LocalDate fromDate) {
    return jpa.deleteFutureFrom(doctorId, fromDate);
  }
}
