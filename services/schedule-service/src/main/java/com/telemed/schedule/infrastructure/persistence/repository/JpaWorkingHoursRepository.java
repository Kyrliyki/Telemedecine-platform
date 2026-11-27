package com.telemed.schedule.infrastructure.persistence.repository;

import com.telemed.schedule.infrastructure.persistence.entity.WorkingHoursEntity;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaWorkingHoursRepository extends JpaRepository<WorkingHoursEntity, UUID> {

  List<WorkingHoursEntity> findAllByDoctorIdOrderByDayOfWeekAscStartTimeAsc(UUID doctorId);

  void deleteAllByDoctorId(UUID doctorId);

  @Modifying
  @Query(
      """
      UPDATE WorkingHoursEntity w
      SET w.effectiveTo = :endDate
      WHERE w.doctorId = :doctorId
        AND w.effectiveTo IS NULL
        AND w.effectiveFrom < :fromDate
      """)
  int closeOpenBefore(
      @Param("doctorId") UUID doctorId,
      @Param("fromDate") LocalDate fromDate,
      @Param("endDate") LocalDate endDate);

  @Modifying
  @Query(
      "DELETE FROM WorkingHoursEntity w WHERE w.doctorId = :doctorId AND w.effectiveFrom >= :fromDate")
  int deleteFutureFrom(@Param("doctorId") UUID doctorId, @Param("fromDate") LocalDate fromDate);
}
