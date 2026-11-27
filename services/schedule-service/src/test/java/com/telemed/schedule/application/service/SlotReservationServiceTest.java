package com.telemed.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.schedule.application.dto.command.ReleaseSlotCommand;
import com.telemed.schedule.application.dto.command.ReserveSlotCommand;
import com.telemed.schedule.application.exception.SlotUnavailableException;
import com.telemed.schedule.application.mapper.ScheduleViewMapper;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.ScheduleExceptionRepository;
import com.telemed.schedule.application.port.output.TimeSlotRepository;
import com.telemed.schedule.application.port.output.WorkingHoursRepository;
import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.domain.model.WorkingHours;
import com.telemed.schedule.infrastructure.persistence.entity.ScheduleOutboxEvent;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class SlotReservationServiceTest {

  private static final UUID DOCTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant START = Instant.parse("2026-01-05T09:00:00Z");

  @Mock private WorkingHoursRepository workingHoursRepository;
  @Mock private ScheduleExceptionRepository exceptionRepository;
  @Mock private TimeSlotRepository timeSlotRepository;
  @Mock private OutboxRepository outboxRepository;
  @Mock private Clock clock;

  private SlotReservationService service;

  @BeforeEach
  void setUp() {
    org.mockito.Mockito.lenient().when(clock.now()).thenReturn(NOW);
    org.mockito.Mockito.lenient()
        .when(workingHoursRepository.findAllByDoctorId(DOCTOR))
        .thenReturn(List.of(rule()));
    org.mockito.Mockito.lenient()
        .when(exceptionRepository.findAllByDoctorId(DOCTOR))
        .thenReturn(List.of());
    JsonMapper jsonMapper = JsonMapper.builder().build();
    service =
        new SlotReservationService(
            workingHoursRepository,
            exceptionRepository,
            timeSlotRepository,
            new ScheduleEventWriter(
                outboxRepository,
                new OutboxEventFactory("schedule-service", ScheduleOutboxEvent::new),
                jsonMapper),
            new ScheduleViewMapper(),
            clock,
            new BookingPolicy(Duration.ZERO, Duration.ofDays(3650)),
            new ScheduleMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
            new ScheduleAuditLogger(),
            15);
  }

  @Test
  void holdsFreeSlotAndWritesOutboxEvent() {
    UUID appointmentId = UUID.randomUUID();
    when(timeSlotRepository.findByDoctorIdAndStartsAt(DOCTOR, START)).thenReturn(Optional.empty());
    when(timeSlotRepository.save(any(TimeSlot.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var slot = service.hold(new ReserveSlotCommand(DOCTOR, START, appointmentId));

    assertThat(slot.appointmentId()).isEqualTo(appointmentId);
    assertThat(slot.status().name()).isEqualTo("HELD");
    assertThat(slot.holdExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    verify(timeSlotRepository).save(any(TimeSlot.class));
    verify(outboxRepository).save(any());
  }

  @Test
  void holdIsIdempotentForSameAppointment() {
    UUID appointmentId = UUID.randomUUID();
    TimeSlot existing =
        TimeSlot.held(
            DOCTOR,
            START,
            Instant.parse("2026-01-05T09:30:00Z"),
            appointmentId,
            NOW.plus(Duration.ofMinutes(15)),
            "STANDARD",
            null,
            NOW);
    when(timeSlotRepository.findByDoctorIdAndStartsAt(DOCTOR, START))
        .thenReturn(Optional.of(existing));

    var slot = service.hold(new ReserveSlotCommand(DOCTOR, START, appointmentId));

    assertThat(slot.appointmentId()).isEqualTo(appointmentId);
    verify(timeSlotRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  void rejectsSlotHeldByAnotherAppointment() {
    TimeSlot existing =
        TimeSlot.held(
            DOCTOR,
            START,
            Instant.parse("2026-01-05T09:30:00Z"),
            UUID.randomUUID(),
            NOW.plus(Duration.ofMinutes(15)),
            "STANDARD",
            null,
            NOW);
    when(timeSlotRepository.findByDoctorIdAndStartsAt(DOCTOR, START))
        .thenReturn(Optional.of(existing));

    assertThatThrownBy(() -> service.hold(new ReserveSlotCommand(DOCTOR, START, UUID.randomUUID())))
        .isInstanceOf(SlotUnavailableException.class);
  }

  @Test
  void confirmsHeldSlot() {
    UUID appointmentId = UUID.randomUUID();
    TimeSlot existing =
        TimeSlot.held(
            DOCTOR,
            START,
            Instant.parse("2026-01-05T09:30:00Z"),
            appointmentId,
            NOW.plus(Duration.ofMinutes(15)),
            "STANDARD",
            null,
            NOW);
    when(timeSlotRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(existing));
    when(timeSlotRepository.save(any(TimeSlot.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    var slot = service.confirm(appointmentId);

    assertThat(slot.status().name()).isEqualTo("CONFIRMED");
    assertThat(slot.holdExpiresAt()).isNull();
    verify(outboxRepository).save(any());
  }

  @Test
  void releasesHeldSlotAndWritesOutboxEvent() {
    UUID appointmentId = UUID.randomUUID();
    TimeSlot existing =
        TimeSlot.held(
            DOCTOR,
            START,
            Instant.parse("2026-01-05T09:30:00Z"),
            appointmentId,
            NOW.plus(Duration.ofMinutes(15)),
            "STANDARD",
            null,
            NOW);
    when(timeSlotRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.of(existing));
    when(timeSlotRepository.save(any(TimeSlot.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.release(new ReleaseSlotCommand(appointmentId))).isTrue();

    assertThat(existing.isHeld()).isFalse();
    verify(timeSlotRepository).save(existing);
    verify(outboxRepository).save(any());
  }

  @Test
  void releasesExpiredHolds() {
    UUID appointmentId = UUID.randomUUID();
    TimeSlot expired =
        TimeSlot.held(
            DOCTOR,
            START,
            Instant.parse("2026-01-05T09:30:00Z"),
            appointmentId,
            NOW.minus(Duration.ofMinutes(1)),
            "STANDARD",
            null,
            NOW.minus(Duration.ofMinutes(20)));
    when(timeSlotRepository.findExpiredHolds(any(), anyInt())).thenReturn(List.of(expired));
    when(timeSlotRepository.save(any(TimeSlot.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.releaseExpiredHolds();

    assertThat(expired.isHeld()).isFalse();
    verify(timeSlotRepository).save(expired);
    verify(outboxRepository).save(any());
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
