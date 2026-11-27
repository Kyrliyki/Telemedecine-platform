package com.telemed.schedule.application.service;

import com.telemed.common.events.ScheduleEventPayloads.SlotConfirmedPayload;
import com.telemed.common.events.ScheduleEventPayloads.SlotHoldExpiredPayload;
import com.telemed.common.events.ScheduleEventPayloads.SlotReleasedPayload;
import com.telemed.common.events.ScheduleEventPayloads.SlotReservedPayload;
import com.telemed.common.events.ScheduleEventTypes;
import com.telemed.schedule.application.dto.command.ReleaseSlotCommand;
import com.telemed.schedule.application.dto.command.ReserveSlotCommand;
import com.telemed.schedule.application.dto.view.SlotView;
import com.telemed.schedule.application.exception.SlotNotFoundException;
import com.telemed.schedule.application.exception.SlotUnavailableException;
import com.telemed.schedule.application.mapper.ScheduleViewMapper;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.ScheduleExceptionRepository;
import com.telemed.schedule.application.port.output.TimeSlotRepository;
import com.telemed.schedule.application.port.output.WorkingHoursRepository;
import com.telemed.schedule.domain.model.ScheduleException;
import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.domain.model.WorkingHours;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlotReservationService {

  private static final Logger log = LoggerFactory.getLogger(SlotReservationService.class);

  private final WorkingHoursRepository workingHoursRepository;
  private final ScheduleExceptionRepository exceptionRepository;
  private final TimeSlotRepository timeSlotRepository;
  private final ScheduleEventWriter eventWriter;
  private final ScheduleViewMapper mapper;
  private final Clock clock;
  private final BookingPolicy bookingPolicy;
  private final ScheduleMetrics metrics;
  private final ScheduleAuditLogger audit;
  private final UserValidationService userValidation;
  private final long holdMinutes;

  public SlotReservationService(
      WorkingHoursRepository workingHoursRepository,
      ScheduleExceptionRepository exceptionRepository,
      TimeSlotRepository timeSlotRepository,
      ScheduleEventWriter eventWriter,
      ScheduleViewMapper mapper,
      Clock clock,
      BookingPolicy bookingPolicy,
      ScheduleMetrics metrics,
      ScheduleAuditLogger audit,
      long holdMinutes) {
    this(
        workingHoursRepository,
        exceptionRepository,
        timeSlotRepository,
        eventWriter,
        mapper,
        clock,
        bookingPolicy,
        metrics,
        audit,
        null,
        holdMinutes);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public SlotReservationService(
      WorkingHoursRepository workingHoursRepository,
      ScheduleExceptionRepository exceptionRepository,
      TimeSlotRepository timeSlotRepository,
      ScheduleEventWriter eventWriter,
      ScheduleViewMapper mapper,
      Clock clock,
      BookingPolicy bookingPolicy,
      ScheduleMetrics metrics,
      ScheduleAuditLogger audit,
      UserValidationService userValidation,
      @Value("${schedule.hold-minutes:15}") long holdMinutes) {
    this.workingHoursRepository = workingHoursRepository;
    this.exceptionRepository = exceptionRepository;
    this.timeSlotRepository = timeSlotRepository;
    this.eventWriter = eventWriter;
    this.mapper = mapper;
    this.clock = clock;
    this.bookingPolicy = bookingPolicy;
    this.metrics = metrics;
    this.audit = audit;
    this.userValidation = userValidation;
    this.holdMinutes = Math.max(1, holdMinutes);
  }

  @Transactional
  public SlotView hold(ReserveSlotCommand command) {
    UUID doctorId = command.doctorId();
    Instant startsAt = command.startsAt();
    UUID appointmentId = command.appointmentId();
    if (doctorId == null || startsAt == null || appointmentId == null) {
      throw new IllegalArgumentException("doctorId, startsAt and appointmentId are required");
    }
    if (userValidation != null) {
      userValidation.requireActiveDoctor(doctorId);
      userValidation.requireActivePatient(command.patientId());
    }
    Instant now = clock.now();
    if (!bookingPolicy.isBookable(startsAt, now)) {
      throw new SlotUnavailableException("Time is outside the booking window");
    }

    WorkingHours rule = resolveRule(doctorId, startsAt, now);
    Instant endsAt = startsAt.plus(Duration.ofMinutes(rule.getSlotDurationMinutes()));
    List<ScheduleException> exceptions = exceptionRepository.findAllByDoctorId(doctorId);
    if (exceptions.stream().anyMatch(item -> item.blocks(startsAt, endsAt))) {
      throw new SlotUnavailableException("Doctor is unavailable at this time");
    }

    Optional<TimeSlot> existing = timeSlotRepository.findByDoctorIdAndStartsAt(doctorId, startsAt);
    Instant holdExpiresAt = now.plus(Duration.ofMinutes(holdMinutes));
    TimeSlot slot;
    if (existing.isPresent()) {
      slot = existing.get();
      if (slot.isHeld() && slot.isExpired(now)) {
        slot.release(now);
        slot.hold(appointmentId, holdExpiresAt, now);
        slot = timeSlotRepository.save(slot);
      } else if (slot.isHeld() || slot.isConfirmed()) {
        if (appointmentId.equals(slot.getAppointmentId())) {
          return mapper.toView(slot);
        }
        metrics.conflict();
        throw new SlotUnavailableException("Slot is already reserved");
      } else {
        slot.hold(appointmentId, holdExpiresAt, now);
        slot = timeSlotRepository.save(slot);
      }
    } else {
      slot =
          TimeSlot.held(
              doctorId,
              startsAt,
              endsAt,
              appointmentId,
              holdExpiresAt,
              rule.getSlotType(),
              rule.getLocation(),
              now);
      try {
        slot = timeSlotRepository.save(slot);
      } catch (DataIntegrityViolationException exception) {
        metrics.conflict();
        throw new SlotUnavailableException("Slot was just reserved by another request");
      }
    }

    eventWriter.write(
        ScheduleEventTypes.SLOT_RESERVED,
        new SlotReservedPayload(
            slot.getId(), doctorId, appointmentId, slot.getStartsAt(), slot.getEndsAt()),
        now);
    metrics.held();
    audit.slotHeld(doctorId, slot.getId(), appointmentId);
    return mapper.toView(slot);
  }

  @Transactional
  public SlotView confirm(UUID appointmentId) {
    TimeSlot slot =
        timeSlotRepository
            .findByAppointmentId(appointmentId)
            .orElseThrow(() -> new SlotNotFoundException("Slot not found for appointment"));
    Instant now = clock.now();
    if (slot.isHeld() && slot.isExpired(now)) {
      slot.release(now);
      timeSlotRepository.save(slot);
      throw new SlotUnavailableException("Hold has expired");
    }
    if (slot.isConfirmed()) {
      return mapper.toView(slot);
    }
    if (!slot.isHeld()) {
      throw new SlotNotFoundException("No active hold for appointment");
    }
    slot.confirm(now);
    TimeSlot saved = timeSlotRepository.save(slot);
    eventWriter.write(
        ScheduleEventTypes.SLOT_CONFIRMED,
        new SlotConfirmedPayload(
            saved.getId(),
            saved.getDoctorId(),
            appointmentId,
            saved.getStartsAt(),
            saved.getEndsAt(),
            now),
        now);
    metrics.confirmed();
    audit.slotConfirmed(saved.getDoctorId(), saved.getId(), appointmentId);
    return mapper.toView(saved);
  }

  @Transactional
  public boolean release(ReleaseSlotCommand command) {
    if (command.appointmentId() == null) {
      throw new IllegalArgumentException("appointmentId is required");
    }
    Optional<TimeSlot> existing = timeSlotRepository.findByAppointmentId(command.appointmentId());
    if (existing.isEmpty()) {
      return false;
    }
    TimeSlot slot = existing.get();
    if (!slot.isHeld() && !slot.isConfirmed()) {
      return true;
    }
    Instant now = clock.now();
    UUID appointmentId = slot.getAppointmentId();
    slot.release(now);
    TimeSlot saved = timeSlotRepository.save(slot);
    eventWriter.write(
        ScheduleEventTypes.SLOT_RELEASED,
        new SlotReleasedPayload(
            saved.getId(), saved.getDoctorId(), appointmentId, saved.getStartsAt(), now),
        now);
    metrics.released();
    audit.slotReleased(saved.getDoctorId(), saved.getId(), appointmentId);
    return true;
  }

  @Transactional
  @Scheduled(fixedDelayString = "${schedule.hold-cleanup-interval:60000}")
  public void releaseExpiredHolds() {
    Instant now = clock.now();
    for (TimeSlot slot : timeSlotRepository.findExpiredHolds(now, 500)) {
      UUID appointmentId = slot.getAppointmentId();
      slot.release(now);
      TimeSlot saved = timeSlotRepository.save(slot);
      eventWriter.write(
          ScheduleEventTypes.SLOT_HOLD_EXPIRED,
          new SlotHoldExpiredPayload(
              saved.getId(), saved.getDoctorId(), appointmentId, saved.getStartsAt(), now),
          now);
      metrics.expired();
      audit.slotHoldExpired(saved.getDoctorId(), saved.getId(), appointmentId);
      log.info("Released expired hold slotId={} appointmentId={}", saved.getId(), appointmentId);
    }
  }

  @Transactional(readOnly = true)
  public SlotView findById(UUID slotId) {
    return timeSlotRepository
        .findById(slotId)
        .map(mapper::toView)
        .orElseThrow(() -> new SlotNotFoundException("Slot not found"));
  }

  @Transactional(readOnly = true)
  public SlotView findByAppointment(UUID appointmentId) {
    return timeSlotRepository
        .findByAppointmentId(appointmentId)
        .map(mapper::toView)
        .orElseThrow(() -> new SlotNotFoundException("Slot not found for appointment"));
  }

  private WorkingHours resolveRule(UUID doctorId, Instant startsAt, Instant now) {
    List<WorkingHours> rules = workingHoursRepository.findAllByDoctorId(doctorId);
    if (rules.isEmpty()) {
      throw new SlotUnavailableException("Doctor has no working hours");
    }
    ZoneId zone = rules.getFirst().getTimezone();
    ZonedDateTime zoned = startsAt.atZone(zone);
    LocalTime local = zoned.toLocalTime();
    int day = zoned.getDayOfWeek().getValue();
    LocalDate date = zoned.toLocalDate();

    return rules.stream()
        .filter(rule -> rule.isEffectiveOn(date))
        .filter(rule -> rule.getDayOfWeek() == day)
        .filter(
            rule -> {
              LocalTime end = local.plusMinutes(rule.getSlotDurationMinutes());
              long offset = Duration.between(rule.getStartTime(), local).toMinutes();
              return !local.isBefore(rule.getStartTime())
                  && !end.isAfter(rule.getEndTime())
                  && offset % rule.getSlotDurationMinutes() == 0;
            })
        .findFirst()
        .orElseThrow(() -> new SlotUnavailableException("Time is outside working hours"));
  }
}
