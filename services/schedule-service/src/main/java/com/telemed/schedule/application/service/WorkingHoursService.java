package com.telemed.schedule.application.service;

import com.telemed.common.events.ScheduleEventPayloads.AvailabilityChangedPayload;
import com.telemed.common.events.ScheduleEventTypes;
import com.telemed.schedule.application.dto.command.UpdateWorkingHoursCommand;
import com.telemed.schedule.application.dto.view.WorkingHoursView;
import com.telemed.schedule.application.mapper.ScheduleViewMapper;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.WorkingHoursRepository;
import com.telemed.schedule.domain.model.WorkingHours;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkingHoursService {

  private final WorkingHoursRepository repository;
  private final ScheduleViewMapper mapper;
  private final ScheduleEventWriter eventWriter;
  private final ScheduleAuditLogger audit;
  private final Clock clock;
  private final UserValidationService userValidation;

  public WorkingHoursService(
      WorkingHoursRepository repository,
      ScheduleViewMapper mapper,
      ScheduleEventWriter eventWriter,
      ScheduleAuditLogger audit,
      Clock clock,
      UserValidationService userValidation) {
    this.repository = repository;
    this.mapper = mapper;
    this.eventWriter = eventWriter;
    this.audit = audit;
    this.clock = clock;
    this.userValidation = userValidation;
  }

  @Transactional(readOnly = true)
  public List<WorkingHoursView> get(UUID doctorId) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    return repository.findAllByDoctorId(doctorId).stream()
        .filter(rule -> rule.getEffectiveTo() == null || !rule.getEffectiveTo().isBefore(today))
        .map(mapper::toView)
        .toList();
  }

  @Transactional
  public List<WorkingHoursView> replace(UUID doctorId, UpdateWorkingHoursCommand command) {
    userValidation.requireActiveDoctor(doctorId);
    if (command.effectiveFrom() == null) {
      throw new IllegalArgumentException("effectiveFrom is required");
    }
    LocalDate effectiveFrom = command.effectiveFrom();
    LocalDate effectiveTo = command.effectiveTo();
    if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
      throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom");
    }

    List<UpdateWorkingHoursCommand.Entry> entries =
        command.entries() == null ? List.of() : command.entries();
    if (entries.isEmpty()) {
      repository.closeOpenBefore(doctorId, effectiveFrom, effectiveFrom.minusDays(1));
      repository.deleteFutureFrom(doctorId, effectiveFrom);
      publishAvailabilityChanged(doctorId, "working-hours.cleared", effectiveFrom);
      return List.of();
    }

    ZoneId timezone = ZoneId.of(entries.getFirst().timezone());
    Set<String> keys = new HashSet<>();
    validateNoOverlaps(entries);
    Instant now = clock.now();
    List<WorkingHours> domain =
        entries.stream()
            .map(
                entry -> {
                  if (!timezone.getId().equals(ZoneId.of(entry.timezone()).getId())) {
                    throw new IllegalArgumentException(
                        "All working hours for a doctor must use the same timezone");
                  }
                  String key = entry.dayOfWeek() + "|" + entry.startTime() + "|" + effectiveFrom;
                  if (!keys.add(key)) {
                    throw new IllegalArgumentException("Duplicate working hours entry: " + key);
                  }
                  return WorkingHours.create(
                      doctorId,
                      entry.dayOfWeek(),
                      entry.startTime(),
                      entry.endTime(),
                      entry.slotDurationMinutes(),
                      entry.bufferMinutes(),
                      timezone,
                      effectiveFrom,
                      effectiveTo,
                      entry.slotType(),
                      entry.location(),
                      now);
                })
            .toList();

    repository.closeOpenBefore(doctorId, effectiveFrom, effectiveFrom.minusDays(1));
    repository.deleteFutureFrom(doctorId, effectiveFrom);
    List<WorkingHoursView> saved = repository.saveAll(domain).stream().map(mapper::toView).toList();
    publishAvailabilityChanged(doctorId, "working-hours.replaced", effectiveFrom);
    audit.workingHoursReplaced(doctorId, saved.size());
    return saved;
  }

  private void publishAvailabilityChanged(UUID doctorId, String reason, LocalDate effectiveFrom) {
    Instant now = clock.now();
    eventWriter.write(
        ScheduleEventTypes.AVAILABILITY_CHANGED,
        new AvailabilityChangedPayload(
            doctorId, reason, now, effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant()),
        now);
  }

  private static void validateNoOverlaps(List<UpdateWorkingHoursCommand.Entry> entries) {
    Map<Integer, List<UpdateWorkingHoursCommand.Entry>> byDay =
        entries.stream().collect(Collectors.groupingBy(UpdateWorkingHoursCommand.Entry::dayOfWeek));
    for (List<UpdateWorkingHoursCommand.Entry> dayEntries : byDay.values()) {
      dayEntries.stream()
          .sorted(Comparator.comparing(UpdateWorkingHoursCommand.Entry::startTime))
          .reduce(
              null,
              (previous, current) -> {
                if (previous != null && current.startTime().isBefore(previous.endTime())) {
                  throw new IllegalArgumentException(
                      "Working hours overlap on day " + current.dayOfWeek());
                }
                return current;
              });
    }
  }
}
