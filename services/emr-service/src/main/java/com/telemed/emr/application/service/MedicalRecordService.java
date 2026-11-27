package com.telemed.emr.application.service;

import com.telemed.common.events.EmrEventPayloads.MedicalRecordPayload;
import com.telemed.common.events.EmrEventTypes;
import com.telemed.emr.application.dto.command.AddAttachmentCommand;
import com.telemed.emr.application.dto.command.CreateMedicalRecordCommand;
import com.telemed.emr.application.dto.command.ReplacePrescriptionsCommand;
import com.telemed.emr.application.dto.command.UpdateMedicalRecordCommand;
import com.telemed.emr.application.dto.command.UpsertVitalSignsCommand;
import com.telemed.emr.application.dto.query.MedicalRecordQuery;
import com.telemed.emr.application.dto.view.EmrAuditView;
import com.telemed.emr.application.dto.view.MedicalRecordRevisionView;
import com.telemed.emr.application.dto.view.MedicalRecordView;
import com.telemed.emr.application.dto.view.PageResult;
import com.telemed.emr.application.exception.MedicalRecordNotFoundException;
import com.telemed.emr.application.mapper.EmrViewMapper;
import com.telemed.emr.application.port.output.AttachmentRepository;
import com.telemed.emr.application.port.output.Clock;
import com.telemed.emr.application.port.output.MedicalRecordRepository;
import com.telemed.emr.application.port.output.PrescriptionRepository;
import com.telemed.emr.application.port.output.VitalSignsRepository;
import com.telemed.emr.domain.model.MedicalAttachment;
import com.telemed.emr.domain.model.MedicalRecord;
import com.telemed.emr.domain.model.Prescription;
import com.telemed.emr.domain.model.VitalSigns;
import com.telemed.emr.domain.valueobject.RecordStatus;
import com.telemed.emr.infrastructure.client.AppointmentValidationClient;
import com.telemed.emr.infrastructure.client.FileValidationClient;
import com.telemed.emr.infrastructure.persistence.entity.MedicalRecordRevisionEntity;
import com.telemed.emr.infrastructure.persistence.repository.JpaMedicalRecordRevisionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class MedicalRecordService {

  private final MedicalRecordRepository recordRepository;
  private final PrescriptionRepository prescriptionRepository;
  private final VitalSignsRepository vitalSignsRepository;
  private final AttachmentRepository attachmentRepository;
  private final EmrEventWriter eventWriter;
  private final EmrViewMapper mapper;
  private final EmrAuditLogger audit;
  private final EmrMetrics metrics;
  private final JpaMedicalRecordRevisionRepository revisionRepository;
  private final JsonMapper jsonMapper;
  private final AppointmentValidationClient appointmentClient;
  private final FileValidationClient fileClient;
  private final boolean requireAppointment;
  private final long retentionDays;
  private final Clock clock;

  public MedicalRecordService(
      MedicalRecordRepository recordRepository,
      PrescriptionRepository prescriptionRepository,
      VitalSignsRepository vitalSignsRepository,
      AttachmentRepository attachmentRepository,
      EmrEventWriter eventWriter,
      EmrViewMapper mapper,
      EmrAuditLogger audit,
      EmrMetrics metrics,
      JpaMedicalRecordRevisionRepository revisionRepository,
      JsonMapper jsonMapper,
      AppointmentValidationClient appointmentClient,
      FileValidationClient fileClient,
      @Value("${emr.require-appointment:true}") boolean requireAppointment,
      @Value("${emr.retention-days:3650}") long retentionDays,
      Clock clock) {
    this.recordRepository = recordRepository;
    this.prescriptionRepository = prescriptionRepository;
    this.vitalSignsRepository = vitalSignsRepository;
    this.attachmentRepository = attachmentRepository;
    this.eventWriter = eventWriter;
    this.mapper = mapper;
    this.audit = audit;
    this.metrics = metrics;
    this.revisionRepository = revisionRepository;
    this.jsonMapper = jsonMapper;
    this.appointmentClient = appointmentClient;
    this.fileClient = fileClient;
    this.requireAppointment = requireAppointment;
    this.retentionDays = retentionDays;
    this.clock = clock;
  }

  @Transactional
  public MedicalRecordView create(UUID doctorId, CreateMedicalRecordCommand command) {
    if (command.patientId() == null) {
      throw new IllegalArgumentException("patientId is required");
    }
    if (command.patientId().equals(doctorId)) {
      throw new IllegalArgumentException("doctor and patient must be different users");
    }
    if (command.appointmentId() == null && requireAppointment) {
      throw new IllegalArgumentException("appointmentId is required");
    }
    validateAppointment(command, doctorId);
    if (command.appointmentId() != null) {
      var existing = recordRepository.findByAppointmentId(command.appointmentId());
      if (existing.isPresent()) {
        MedicalRecord record = existing.get();
        if (!record.getPatientId().equals(command.patientId())
            || !record.getDoctorId().equals(doctorId)) {
          throw new IllegalStateException("Appointment is already linked to another record");
        }
        return loadView(record);
      }
    }

    Instant now = clock.now();
    MedicalRecord record =
        MedicalRecord.draft(command.patientId(), doctorId, command.appointmentId(), now);
    if (retentionDays > 0) {
      record.setRetentionUntil(now.plus(Duration.ofDays(retentionDays)), now);
    }
    record.update(
        command.chiefComplaint(),
        command.diagnosisCode(),
        command.diagnosisText(),
        command.notes(),
        command.treatmentPlan(),
        now);
    MedicalRecord saved = recordRepository.save(record);
    publish(EmrEventTypes.RECORD_CREATED, saved, now);
    saveRevision(saved, doctorId, "CREATE", now);
    metrics.created();
    audit.recordCreated(saved.getId(), saved.getPatientId(), saved.getDoctorId());
    return loadView(saved);
  }

  @Transactional(readOnly = true)
  public MedicalRecordView get(UUID id, UUID requesterId, Set<String> roles) {
    MedicalRecord record = load(id);
    if (!canView(record, requesterId, roles)) {
      audit.accessDenied(id, requesterId, "view");
      throw new AccessDeniedException("Access to medical record denied");
    }
    audit.recordViewed(id, requesterId);
    return loadView(record);
  }

  @Transactional(readOnly = true)
  public PageResult<MedicalRecordView> byPatient(
      UUID patientId, MedicalRecordQuery query, UUID requesterId, Set<String> roles) {
    boolean admin = roles.contains("ADMIN");
    boolean self = patientId.equals(requesterId);
    boolean doctor = roles.contains("DOCTOR");
    if (!admin && !self && !doctor) {
      audit.accessDenied(null, requesterId, "patient-records");
      throw new AccessDeniedException("Access to patient records denied");
    }
    PageResult<MedicalRecord> page =
        admin || self
            ? recordRepository.findByPatient(
                patientId, parseStatus(query.status()), safePage(query), safeSize(query))
            : recordRepository.findByPatientAndDoctor(
                patientId,
                requesterId,
                parseStatus(query.status()),
                safePage(query),
                safeSize(query));
    return mapPage(page);
  }

  @Transactional(readOnly = true)
  public PageResult<MedicalRecordView> mine(
      UUID requesterId, Set<String> roles, MedicalRecordQuery query, String view) {
    boolean doctorView;
    if (view == null || view.isBlank()) {
      doctorView = roles.contains("DOCTOR");
    } else {
      String normalized = view.toUpperCase(Locale.ROOT);
      if (!normalized.equals("PATIENT") && !normalized.equals("DOCTOR")) {
        throw new IllegalArgumentException("view must be 'patient' or 'doctor'");
      }
      if (!roles.contains(normalized)) {
        throw new AccessDeniedException("the requested view requires the " + normalized + " role");
      }
      doctorView = normalized.equals("DOCTOR");
    }

    PageResult<MedicalRecord> page =
        doctorView
            ? recordRepository.findByDoctor(
                requesterId, parseStatus(query.status()), safePage(query), safeSize(query))
            : recordRepository.findByPatient(
                requesterId, parseStatus(query.status()), safePage(query), safeSize(query));
    return mapPage(page);
  }

  @Transactional
  public MedicalRecordView update(
      UUID id, UpdateMedicalRecordCommand command, UUID requesterId, boolean admin) {
    MedicalRecord record = load(id);
    requireAuthorOrAdmin(record, requesterId, admin);
    Instant now = clock.now();
    record.update(
        command.chiefComplaint(),
        command.diagnosisCode(),
        command.diagnosisText(),
        command.notes(),
        command.treatmentPlan(),
        now);
    MedicalRecord saved = recordRepository.save(record);
    publish(EmrEventTypes.RECORD_UPDATED, saved, now);
    saveRevision(saved, requesterId, "UPDATE", now);
    audit.recordUpdated(saved.getId(), requesterId);
    return loadView(saved);
  }

  @Transactional
  public MedicalRecordView finalizeRecord(UUID id, UUID requesterId, boolean admin) {
    MedicalRecord record = load(id);
    requireAuthorOrAdmin(record, requesterId, admin);
    Instant now = clock.now();
    record.finalizeRecord(now);
    MedicalRecord saved = recordRepository.save(record);
    publish(EmrEventTypes.RECORD_FINALIZED, saved, now);
    saveRevision(saved, requesterId, "FINALIZE", now);
    metrics.finalized();
    audit.recordFinalized(id, requesterId);
    return loadView(saved);
  }

  @Transactional
  public MedicalRecordView amend(UUID id, UUID requesterId, boolean admin) {
    MedicalRecord record = load(id);
    requireAuthorOrAdmin(record, requesterId, admin);
    Instant now = clock.now();
    record.amend(now);
    MedicalRecord saved = recordRepository.save(record);
    publish(EmrEventTypes.RECORD_AMENDED, saved, now);
    saveRevision(saved, requesterId, "AMEND", now);
    metrics.amended();
    audit.recordAmended(id, requesterId);
    return loadView(saved);
  }

  @Transactional
  public MedicalRecordView replacePrescriptions(
      UUID id, ReplacePrescriptionsCommand command, UUID requesterId, boolean admin) {
    MedicalRecord record = loadEditable(id, requesterId, admin);
    Instant now = clock.now();
    List<Prescription> prescriptions =
        (command.entries() == null
                ? List.<ReplacePrescriptionsCommand.Entry>of()
                : command.entries())
            .stream()
                .map(
                    entry ->
                        Prescription.create(
                            id,
                            entry.medicationName(),
                            entry.dosage(),
                            entry.frequency(),
                            entry.durationDays(),
                            entry.instructions(),
                            now))
                .toList();
    prescriptionRepository.replaceForRecord(id, prescriptions);
    record.update(null, null, null, null, null, now);
    MedicalRecord saved = recordRepository.save(record);
    publish(EmrEventTypes.RECORD_UPDATED, saved, now);
    saveRevision(saved, requesterId, "PRESCRIPTIONS", now);
    return loadView(saved);
  }

  @Transactional
  public MedicalRecordView upsertVitalSigns(
      UUID id, UpsertVitalSignsCommand command, UUID requesterId, boolean admin) {
    MedicalRecord record = loadEditable(id, requesterId, admin);
    Instant now = clock.now();
    VitalSigns signs =
        VitalSigns.create(
            id,
            command.heightCm(),
            command.weightKg(),
            command.systolic(),
            command.diastolic(),
            command.pulse(),
            command.temperatureC(),
            command.spo2(),
            now);
    vitalSignsRepository.save(signs);
    record.update(null, null, null, null, null, now);
    MedicalRecord saved = recordRepository.save(record);
    publish(EmrEventTypes.RECORD_UPDATED, saved, now);
    saveRevision(saved, requesterId, "VITALS", now);
    return loadView(saved);
  }

  @Transactional
  public MedicalRecordView addAttachment(
      UUID id, AddAttachmentCommand command, UUID requesterId, boolean admin) {
    if (command.fileId() == null) {
      throw new IllegalArgumentException("fileId is required");
    }
    MedicalRecord record = loadEditable(id, requesterId, admin);
    Instant now = clock.now();
    fileClient.validate(command.fileId(), record.getPatientId());
    attachmentRepository.save(
        MedicalAttachment.create(id, command.fileId(), command.description(), now));
    record.update(null, null, null, null, null, now);
    MedicalRecord saved = recordRepository.save(record);
    publish(EmrEventTypes.RECORD_UPDATED, saved, now);
    saveRevision(saved, requesterId, "ATTACHMENT", now);
    return loadView(saved);
  }

  @Transactional
  public int deleteByPatient(UUID patientId) {
    for (UUID recordId : recordRepository.findIdsByPatient(patientId)) {
      prescriptionRepository.deleteByRecordId(recordId);
      vitalSignsRepository.deleteByRecordId(recordId);
      attachmentRepository.deleteByRecordId(recordId);
    }
    int count = recordRepository.markDeletedByPatient(patientId, clock.now());
    metrics.deleted(count);
    return count;
  }

  @Transactional
  public int deleteByDoctor(UUID doctorId) {
    for (UUID recordId : recordRepository.findIdsByDoctor(doctorId)) {
      prescriptionRepository.deleteByRecordId(recordId);
      vitalSignsRepository.deleteByRecordId(recordId);
      attachmentRepository.deleteByRecordId(recordId);
    }
    int count = recordRepository.markDeletedByDoctor(doctorId, clock.now());
    metrics.deleted(count);
    return count;
  }

  @Transactional(readOnly = true)
  public java.util.List<MedicalRecordRevisionView> revisions(
      UUID id, UUID requesterId, Set<String> roles) {
    MedicalRecord record = load(id);
    if (!canView(record, requesterId, roles)) {
      audit.accessDenied(id, requesterId, "revisions");
      throw new AccessDeniedException("Access to medical record denied");
    }
    return revisionRepository.findAllByRecordIdOrderByRevisionNumberAsc(id).stream()
        .map(this::toRevisionView)
        .toList();
  }

  @Transactional(readOnly = true)
  public MedicalRecordRevisionView revision(
      UUID id, int revisionNumber, UUID requesterId, Set<String> roles) {
    MedicalRecord record = load(id);
    if (!canView(record, requesterId, roles)) {
      audit.accessDenied(id, requesterId, "revision");
      throw new AccessDeniedException("Access to medical record denied");
    }
    return revisionRepository
        .findByRecordIdAndRevisionNumber(id, revisionNumber)
        .map(this::toRevisionView)
        .orElseThrow(() -> new MedicalRecordNotFoundException(id));
  }

  private void saveRevision(MedicalRecord record, UUID actorId, String reason, Instant now) {
    try {
      MedicalRecordRevisionEntity entity = new MedicalRecordRevisionEntity();
      entity.setId(UUID.randomUUID());
      entity.setRecordId(record.getId());
      entity.setRevisionNumber(revisionRepository.maxRevisionNumber(record.getId()) + 1);
      entity.setChangedBy(actorId);
      entity.setChangeReason(reason);
      entity.setCreatedAt(now);
      entity.setSnapshot(jsonMapper.writeValueAsString(loadView(record)));
      revisionRepository.save(entity);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to store medical record revision", exception);
    }
  }

  private MedicalRecordRevisionView toRevisionView(MedicalRecordRevisionEntity entity) {
    return new MedicalRecordRevisionView(
        entity.getId(),
        entity.getRecordId(),
        entity.getRevisionNumber(),
        entity.getChangedBy(),
        entity.getChangeReason(),
        entity.getSnapshot(),
        entity.getCreatedAt());
  }

  private MedicalRecord load(UUID id) {
    return recordRepository.findById(id).orElseThrow(() -> new MedicalRecordNotFoundException(id));
  }

  private MedicalRecord loadEditable(UUID id, UUID requesterId, boolean admin) {
    MedicalRecord record = load(id);
    requireAuthorOrAdmin(record, requesterId, admin);
    if (!record.getStatus().isEditable()) {
      throw new IllegalStateException("Record is not editable in status " + record.getStatus());
    }
    return record;
  }

  private void requireAuthorOrAdmin(MedicalRecord record, UUID requesterId, boolean admin) {
    if (!admin && !record.getDoctorId().equals(requesterId)) {
      audit.accessDenied(record.getId(), requesterId, "modify");
      throw new AccessDeniedException("Only the authoring doctor can modify this record");
    }
  }

  private boolean canView(MedicalRecord record, UUID requesterId, Set<String> roles) {
    return record.canBeViewedBy(requesterId, roles.contains("ADMIN"));
  }

  private MedicalRecordView loadView(MedicalRecord record) {
    return mapper.toView(
        record,
        prescriptionRepository.findByRecordId(record.getId()),
        vitalSignsRepository.findByRecordId(record.getId()).orElse(null),
        attachmentRepository.findByRecordId(record.getId()));
  }

  private PageResult<MedicalRecordView> mapPage(PageResult<MedicalRecord> page) {
    List<MedicalRecordView> content =
        page.content().stream()
            .map(record -> mapper.toView(record, List.of(), null, List.of()))
            .toList();
    return new PageResult<>(content, page.totalElements(), page.page(), page.size());
  }

  private void publish(String type, MedicalRecord record, Instant now) {
    eventWriter.write(
        type,
        new MedicalRecordPayload(
            record.getId(),
            record.getPatientId(),
            record.getDoctorId(),
            record.getAppointmentId(),
            record.getStatus().name(),
            now),
        now);
  }

  @Transactional
  public MedicalRecordView setLegalHold(UUID id, boolean enabled, UUID requesterId, boolean admin) {
    requireAdmin(admin);
    MedicalRecord record = load(id);
    Instant now = clock.now();
    record.setLegalHold(enabled, now);
    MedicalRecord saved = recordRepository.save(record);
    audit.recordAdminChange(id, requesterId, enabled ? "LEGAL_HOLD_ON" : "LEGAL_HOLD_OFF");
    publish(EmrEventTypes.RECORD_UPDATED, saved, now);
    return loadView(saved);
  }

  @Transactional
  public MedicalRecordView setRetention(
      UUID id, Instant retentionUntil, UUID requesterId, boolean admin) {
    requireAdmin(admin);
    MedicalRecord record = load(id);
    Instant now = clock.now();
    record.setRetentionUntil(retentionUntil, now);
    MedicalRecord saved = recordRepository.save(record);
    audit.recordAdminChange(id, requesterId, "RETENTION_CHANGED");
    publish(EmrEventTypes.RECORD_UPDATED, saved, now);
    return loadView(saved);
  }

  @Transactional
  public java.util.List<MedicalRecordView> exportByPatient(UUID patientId, boolean admin) {
    requireAdmin(admin);
    return recordRepository.findByPatient(patientId, null, 0, 1000).content().stream()
        .map(this::loadView)
        .toList();
  }

  @Transactional
  public int purgeExpired(int limit) {
    int purged = 0;
    for (UUID id : recordRepository.findExpiredIds(clock.now(), limit)) {
      prescriptionRepository.deleteByRecordId(id);
      vitalSignsRepository.deleteByRecordId(id);
      attachmentRepository.deleteByRecordId(id);
      recordRepository.deleteById(id);
      audit.recordPurged(id);
      purged++;
    }
    if (purged > 0) {
      metrics.deleted(purged);
    }
    return purged;
  }

  public java.util.List<EmrAuditView> auditForRecord(UUID id, boolean admin) {
    requireAdmin(admin);
    return audit.forRecord(id);
  }

  private static void requireAdmin(boolean admin) {
    if (!admin) {
      throw new AccessDeniedException("ADMIN role is required");
    }
  }

  private void validateAppointment(CreateMedicalRecordCommand command, UUID doctorId) {
    if (!appointmentClient.enabled() || command.appointmentId() == null) {
      return;
    }
    var appointment =
        appointmentClient
            .find(command.appointmentId())
            .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
    if (!appointment.patientId().equals(command.patientId())
        || !appointment.doctorId().equals(doctorId)) {
      audit.accessDenied(null, doctorId, "appointment-mismatch");
      throw new AccessDeniedException("Appointment does not belong to this doctor/patient");
    }
    if (!"COMPLETED".equalsIgnoreCase(appointment.status())) {
      throw new IllegalStateException("EMR can only be created for a completed appointment");
    }
  }

  private static RecordStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return RecordStatus.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Unknown record status: " + value);
    }
  }

  private static int safePage(MedicalRecordQuery query) {
    return Math.max(0, query.page());
  }

  private static int safeSize(MedicalRecordQuery query) {
    return Math.min(100, Math.max(1, query.size()));
  }
}
