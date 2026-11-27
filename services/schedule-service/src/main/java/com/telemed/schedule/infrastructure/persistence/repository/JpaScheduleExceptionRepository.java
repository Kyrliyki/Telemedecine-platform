package com.telemed.schedule.infrastructure.persistence.repository;

import com.telemed.schedule.infrastructure.persistence.entity.ScheduleExceptionEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaScheduleExceptionRepository
    extends JpaRepository<ScheduleExceptionEntity, UUID> {

  List<ScheduleExceptionEntity> findAllByDoctorIdOrderByStartsAtAsc(UUID doctorId);

  void deleteAllByDoctorId(UUID doctorId);

  @Query(
      """
      SELECT e FROM ScheduleExceptionEntity e
      WHERE e.doctorId = :doctorId
        AND e.startsAt < :to
        AND e.endsAt > :from
      """)
  List<ScheduleExceptionEntity> findOverlapping(
      @Param("doctorId") UUID doctorId, @Param("from") Instant from, @Param("to") Instant to);
}
