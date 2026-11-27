package com.telemed.schedule.application.port.output;

import com.telemed.schedule.domain.model.WorkingHours;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface WorkingHoursRepository {

  List<WorkingHours> findAllByDoctorId(UUID doctorId);

  List<WorkingHours> saveAll(List<WorkingHours> entries);

  void deleteAllByDoctorId(UUID doctorId);

  void closeOpenBefore(UUID doctorId, LocalDate fromDate, LocalDate endDate);

  int deleteFutureFrom(UUID doctorId, LocalDate fromDate);
}
