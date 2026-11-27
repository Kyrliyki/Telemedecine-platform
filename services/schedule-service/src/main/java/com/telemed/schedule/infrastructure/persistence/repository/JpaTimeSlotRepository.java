package com.telemed.schedule.infrastructure.persistence.repository;

import com.telemed.schedule.infrastructure.persistence.entity.TimeSlotEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaTimeSlotRepository extends JpaRepository<TimeSlotEntity, UUID> {

  Optional<TimeSlotEntity> findByDoctorIdAndStartsAt(UUID doctorId, Instant startsAt);

  Optional<TimeSlotEntity> findByAppointmentId(UUID appointmentId);

  void deleteAllByDoctorId(UUID doctorId);

  List<TimeSlotEntity> findAllByStatusAndHoldExpiresAtBefore(
      com.telemed.schedule.domain.valueobject.SlotStatus status, Instant cutoff, Pageable pageable);

  List<TimeSlotEntity> findAllByStatusAndStartsAtAfter(
      com.telemed.schedule.domain.valueobject.SlotStatus status,
      Instant startsAt,
      Pageable pageable);

  @Modifying
  @Query("DELETE FROM TimeSlotEntity s WHERE s.endsAt < :cutoff")
  int deleteEndedBefore(@Param("cutoff") Instant cutoff);

  @Query(
      """
      SELECT s FROM TimeSlotEntity s
      WHERE s.doctorId = :doctorId
        AND s.startsAt < :to
        AND s.endsAt > :from
      """)
  List<TimeSlotEntity> findOverlapping(
      @Param("doctorId") UUID doctorId, @Param("from") Instant from, @Param("to") Instant to);
}
