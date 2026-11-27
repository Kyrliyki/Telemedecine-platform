package com.telemed.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.telemed.schedule.application.mapper.ScheduleViewMapper;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.ScheduleExceptionRepository;
import com.telemed.schedule.application.port.output.TimeSlotRepository;
import com.telemed.schedule.application.port.output.WorkingHoursRepository;
import com.telemed.schedule.domain.model.ScheduleException;
import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.domain.model.WorkingHours;
import com.telemed.schedule.domain.valueobject.ExceptionRecurrence;
import com.telemed.schedule.domain.valueobject.SlotStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

  private static final UUID DOCTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @Mock private WorkingHoursRepository workingHoursRepository;
  @Mock private ScheduleExceptionRepository exceptionRepository;
  @Mock private TimeSlotRepository timeSlotRepository;
  @Mock private Clock clock;

  private AvailabilityService service;

  @BeforeEach
  void setUp() {
    service =
        new AvailabilityService(
            workingHoursRepository,
            exceptionRepository,
            timeSlotRepository,
            new ScheduleViewMapper(),
            clock,
            new BookingPolicy(Duration.ZERO, Duration.ofDays(3650)),
            62,
            500);
    org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
    org.mockito.Mockito.lenient()
        .when(workingHoursRepository.findAllByDoctorId(DOCTOR))
        .thenReturn(List.of(rule()));
    org.mockito.Mockito.lenient()
        .when(exceptionRepository.findAllByDoctorId(DOCTOR))
        .thenReturn(List.of());
    org.mockito.Mockito.lenient()
        .when(timeSlotRepository.findOverlapping(eq(DOCTOR), any(), any()))
        .thenReturn(List.of());
  }

  @Test
  void generatesSlotsFromWorkingHours() {
    LocalDate monday = LocalDate.of(2026, 1, 5);

    var slots = service.availability(DOCTOR, monday, monday);

    assertThat(slots).hasSize(4);
    assertThat(slots.getFirst().startsAt()).isEqualTo(Instant.parse("2026-01-05T09:00:00Z"));
    assertThat(slots.getFirst().status()).isEqualTo(SlotStatus.AVAILABLE);
    assertThat(slots.getLast().endsAt()).isEqualTo(Instant.parse("2026-01-05T11:00:00Z"));
  }

  @Test
  void removesSlotsCoveredByException() {
    LocalDate monday = LocalDate.of(2026, 1, 5);
    org.mockito.Mockito.lenient()
        .when(exceptionRepository.findAllByDoctorId(DOCTOR))
        .thenReturn(
            List.of(
                ScheduleException.create(
                    DOCTOR,
                    Instant.parse("2026-01-05T09:30:00Z"),
                    Instant.parse("2026-01-05T10:00:00Z"),
                    "break",
                    ExceptionRecurrence.NONE,
                    null,
                    NOW)));

    var slots = service.availability(DOCTOR, monday, monday);

    assertThat(slots).hasSize(3);
    assertThat(slots)
        .noneMatch(slot -> slot.startsAt().equals(Instant.parse("2026-01-05T09:30:00Z")));
  }

  @Test
  void marksHeldStoredSlot() {
    LocalDate monday = LocalDate.of(2026, 1, 5);
    UUID appointmentId = UUID.randomUUID();
    TimeSlot held =
        TimeSlot.held(
            DOCTOR,
            Instant.parse("2026-01-05T09:00:00Z"),
            Instant.parse("2026-01-05T09:30:00Z"),
            appointmentId,
            NOW.plus(Duration.ofMinutes(15)),
            "STANDARD",
            null,
            NOW);
    org.mockito.Mockito.lenient()
        .when(timeSlotRepository.findOverlapping(eq(DOCTOR), any(), any()))
        .thenReturn(List.of(held));

    var slots = service.availability(DOCTOR, monday, monday);

    assertThat(slots.getFirst().status()).isEqualTo(SlotStatus.HELD);
    assertThat(slots.getFirst().appointmentId()).isEqualTo(appointmentId);
  }

  @Test
  void supportsRecurringException() {
    LocalDate monday = LocalDate.of(2026, 1, 5);
    org.mockito.Mockito.lenient()
        .when(exceptionRepository.findAllByDoctorId(DOCTOR))
        .thenReturn(
            List.of(
                ScheduleException.create(
                    DOCTOR,
                    Instant.parse("2026-01-05T09:00:00Z"),
                    Instant.parse("2026-01-05T09:30:00Z"),
                    "weekly",
                    ExceptionRecurrence.WEEKLY,
                    Instant.parse("2026-02-01T00:00:00Z"),
                    NOW)));

    var slots = service.availability(DOCTOR, monday.plusDays(7), monday.plusDays(7));

    assertThat(slots).hasSize(3);
  }

  private static WorkingHours rule() {
    return WorkingHours.create(
        DOCTOR,
        1,
        LocalTime.of(9, 0),
        LocalTime.of(11, 0),
        30,
        0,
        ZoneId.of("UTC"),
        LocalDate.of(2026, 1, 1),
        null,
        "STANDARD",
        null,
        NOW);
  }
}
