package com.telemed.schedule.application.service;

import com.telemed.common.events.ScheduleEventPayloads.AvailabilityChangedPayload;
import com.telemed.common.events.ScheduleEventTypes;
import com.telemed.schedule.application.dto.command.CreateScheduleExceptionCommand;
import com.telemed.schedule.application.dto.view.ScheduleExceptionView;
import com.telemed.schedule.application.exception.SlotNotFoundException;
import com.telemed.schedule.application.mapper.ScheduleViewMapper;
import com.telemed.schedule.application.port.output.Clock;
import com.telemed.schedule.application.port.output.ScheduleExceptionRepository;
import com.telemed.schedule.domain.model.ScheduleException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ScheduleExceptionService {

  private final ScheduleExceptionRepository repository;
  private final ScheduleViewMapper mapper;
  private final ScheduleEventWriter eventWriter;
  private final ScheduleAuditLogger audit;
  private final Clock clock;
  private final UserValidationService userValidation;

  public ScheduleExceptionService(
      ScheduleExceptionRepository repository,
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
  public List<ScheduleExceptionView> list(UUID doctorId) {
    return repository.findAllByDoctorId(doctorId).stream().map(mapper::toView).toList();
  }

  @Transactional
  public ScheduleExceptionView create(UUID doctorId, CreateScheduleExceptionCommand command) {
    userValidation.requireActiveDoctor(doctorId);
    Instant now = clock.now();
    ScheduleException exception =
        ScheduleException.create(
            doctorId,
            command.startsAt(),
            command.endsAt(),
            command.reason(),
            command.recurrence(),
            command.recurrenceUntil(),
            now);
    ScheduleException saved = repository.save(exception);
    publishChanged(doctorId, "exception.created", command.startsAt());
    audit.exceptionCreated(doctorId, saved.getId());
    return mapper.toView(saved);
  }

  @Transactional
  public void delete(UUID doctorId, UUID exceptionId) {
    ScheduleException exception =
        repository
            .findById(exceptionId)
            .filter(item -> item.getDoctorId().equals(doctorId))
            .orElseThrow(() -> new SlotNotFoundException("Schedule exception not found"));
    repository.deleteById(exception.getId());
    publishChanged(doctorId, "exception.deleted", exception.getStartsAt());
    audit.exceptionDeleted(doctorId, exception.getId());
  }

  private void publishChanged(UUID doctorId, String reason, Instant effectiveFrom) {
    Instant now = clock.now();
    eventWriter.write(
        ScheduleEventTypes.AVAILABILITY_CHANGED,
        new AvailabilityChangedPayload(doctorId, reason, now, effectiveFrom),
        now);
  }
}
