package com.telemed.schedule.application.service;

import com.telemed.schedule.application.dto.view.SlotView;
import com.telemed.schedule.application.mapper.ScheduleViewMapper;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.ScheduleExceptionRepository;
import com.telemed.schedule.application.port.output.TimeSlotRepository;
import com.telemed.schedule.application.port.output.WorkingHoursRepository;
import com.telemed.schedule.domain.model.ScheduleException;
import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.domain.model.WorkingHours;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AvailabilityService {

  private final WorkingHoursRepository workingHoursRepository;
  private final ScheduleExceptionRepository exceptionRepository;
  private final TimeSlotRepository timeSlotRepository;
  private final ScheduleViewMapper mapper;
  private final Clock clock;
  private final BookingPolicy bookingPolicy;
  private final UserValidationService userValidation;
  private final int maxRangeDays;
  private final int maxSlotsPerResponse;

  public AvailabilityService(
      WorkingHoursRepository workingHoursRepository,
      ScheduleExceptionRepository exceptionRepository,
      TimeSlotRepository timeSlotRepository,
      ScheduleViewMapper mapper,
      Clock clock,
      BookingPolicy bookingPolicy,
      int maxRangeDays,
      int maxSlotsPerResponse) {
    this(
        workingHoursRepository,
        exceptionRepository,
        timeSlotRepository,
        mapper,
        clock,
        bookingPolicy,
        null,
        maxRangeDays,
        maxSlotsPerResponse);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public AvailabilityService(
      WorkingHoursRepository workingHoursRepository,
      ScheduleExceptionRepository exceptionRepository,
      TimeSlotRepository timeSlotRepository,
      ScheduleViewMapper mapper,
      Clock clock,
      BookingPolicy bookingPolicy,
      UserValidationService userValidation,
      @Value("${schedule.max-range-days:62}") int maxRangeDays,
      @Value("${schedule.max-slots-per-response:500}") int maxSlotsPerResponse) {
    this.workingHoursRepository = workingHoursRepository;
    this.exceptionRepository = exceptionRepository;
    this.timeSlotRepository = timeSlotRepository;
    this.mapper = mapper;
    this.clock = clock;
    this.bookingPolicy = bookingPolicy;
    this.userValidation = userValidation;
    this.maxRangeDays = maxRangeDays;
    this.maxSlotsPerResponse = maxSlotsPerResponse;
  }

  @Transactional(readOnly = true)
  public List<SlotView> availability(UUID doctorId, LocalDate from, LocalDate to) {
    return availability(doctorId, from, to, maxSlotsPerResponse);
  }

  @Transactional(readOnly = true)
  public List<SlotView> availability(UUID doctorId, LocalDate from, LocalDate to, int limit) {
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to are required");
    }
    if (to.isBefore(from)) {
      throw new IllegalArgumentException("to must not be before from");
    }
    if (ChronoUnit.DAYS.between(from, to) > maxRangeDays) {
      throw new IllegalArgumentException("Range exceeds " + maxRangeDays + " days");
    }
    if (userValidation != null) {
      userValidation.requireActiveDoctor(doctorId);
    }
    int cappedLimit = limit <= 0 ? maxSlotsPerResponse : Math.min(limit, maxSlotsPerResponse);

    List<WorkingHours> rules = workingHoursRepository.findAllByDoctorId(doctorId);
    if (rules.isEmpty()) {
      return List.of();
    }
    ZoneId zone = rules.getFirst().getTimezone();
    Instant rangeStart = from.atStartOfDay(zone).toInstant();
    Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
    List<ScheduleException> exceptions = exceptionRepository.findAllByDoctorId(doctorId);
    Instant now = clock.now();
    Map<Instant, TimeSlot> stored =
        timeSlotRepository.findOverlapping(doctorId, rangeStart, rangeEnd).stream()
            .filter(slot -> !slot.isExpired(now))
            .collect(
                java.util.stream.Collectors.toMap(
                    TimeSlot::getStartsAt, slot -> slot, (first, second) -> first));

    Map<Instant, SlotView> result = new LinkedHashMap<>();
    for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
      int day = date.getDayOfWeek().getValue();
      for (WorkingHours rule : rules) {
        if (rule.getDayOfWeek() != day || !rule.isEffectiveOn(date)) {
          continue;
        }
        ZonedDateTime cursor = rule.startOn(date);
        ZonedDateTime limitTime = rule.endOn(date);
        while (!cursor.plusMinutes(rule.getSlotDurationMinutes()).isAfter(limitTime)) {
          ZonedDateTime end = cursor.plusMinutes(rule.getSlotDurationMinutes());
          Instant startInstant = cursor.toInstant();
          Instant endInstant = end.toInstant();
          if (!bookingPolicy.isBookable(startInstant, now)
              || isBlocked(exceptions, startInstant, endInstant)) {
            cursor = end.plusMinutes(rule.getBufferMinutes());
            continue;
          }
          result.putIfAbsent(
              startInstant,
              mapper.virtualSlot(
                  doctorId,
                  startInstant,
                  endInstant,
                  stored.get(startInstant),
                  rule.getSlotType(),
                  rule.getLocation()));
          if (result.size() >= cappedLimit) {
            return sorted(result);
          }
          cursor = end.plusMinutes(rule.getBufferMinutes());
        }
      }
    }
    return sorted(result);
  }

  @Transactional(readOnly = true)
  public SlotView nextAvailable(UUID doctorId, LocalDate from) {
    LocalDate to = from.plusDays(Math.max(1, maxRangeDays));
    List<SlotView> slots = availability(doctorId, from, to, 1);
    return slots.stream()
        .filter(
            slot -> slot.status() == com.telemed.schedule.domain.valueobject.SlotStatus.AVAILABLE)
        .findFirst()
        .orElse(null);
  }

  private static List<SlotView> sorted(Map<Instant, SlotView> result) {
    List<SlotView> slots = new ArrayList<>(result.values());
    slots.sort(java.util.Comparator.comparing(SlotView::startsAt));
    return slots;
  }

  private static boolean isBlocked(List<ScheduleException> exceptions, Instant start, Instant end) {
    return exceptions.stream().anyMatch(exception -> exception.blocks(start, end));
  }
}
