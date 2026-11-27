package com.telemed.schedule.application.service;

import com.telemed.schedule.application.port.output.ScheduleExceptionRepository;
import com.telemed.schedule.application.port.output.TimeSlotRepository;
import com.telemed.schedule.application.port.output.WorkingHoursRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleCleanupService {

  private static final Logger log = LoggerFactory.getLogger(ScheduleCleanupService.class);

  private final WorkingHoursRepository workingHoursRepository;
  private final ScheduleExceptionRepository exceptionRepository;
  private final TimeSlotRepository timeSlotRepository;

  public ScheduleCleanupService(
      WorkingHoursRepository workingHoursRepository,
      ScheduleExceptionRepository exceptionRepository,
      TimeSlotRepository timeSlotRepository) {
    this.workingHoursRepository = workingHoursRepository;
    this.exceptionRepository = exceptionRepository;
    this.timeSlotRepository = timeSlotRepository;
  }

  @Transactional
  public void deleteDoctorSchedule(UUID doctorId) {
    workingHoursRepository.deleteAllByDoctorId(doctorId);
    exceptionRepository.deleteAllByDoctorId(doctorId);
    timeSlotRepository.deleteAllByDoctorId(doctorId);
    log.info("Deleted schedule data for doctorId={}", doctorId);
  }
}
