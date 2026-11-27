package com.telemed.emr.infrastructure.persistence.adapter;

import com.telemed.emr.application.dto.view.PageResult;
import com.telemed.emr.application.port.output.MedicalRecordRepository;
import com.telemed.emr.domain.model.MedicalRecord;
import com.telemed.emr.domain.valueobject.RecordStatus;
import com.telemed.emr.infrastructure.persistence.entity.MedicalRecordEntity;
import com.telemed.emr.infrastructure.persistence.mapper.MedicalRecordEntityMapper;
import com.telemed.emr.infrastructure.persistence.repository.JpaMedicalRecordRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class MedicalRecordRepositoryAdapter implements MedicalRecordRepository {

  private final JpaMedicalRecordRepository jpa;
  private final MedicalRecordEntityMapper mapper;

  public MedicalRecordRepositoryAdapter(
      JpaMedicalRecordRepository jpa, MedicalRecordEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public MedicalRecord save(MedicalRecord record) {
    return mapper.toDomain(jpa.save(mapper.toEntity(record)));
  }

  @Override
  public Optional<MedicalRecord> findById(UUID id) {
    return jpa.findById(id).map(mapper::toDomain);
  }

  @Override
  public Optional<MedicalRecord> findByAppointmentId(UUID appointmentId) {
    return jpa.findByAppointmentId(appointmentId).map(mapper::toDomain);
  }

  @Override
  public PageResult<MedicalRecord> findByPatient(
      UUID patientId, RecordStatus status, int page, int size) {
    Page<MedicalRecordEntity> result =
        status == null
            ? jpa.findByPatientId(patientId, page(page, size))
            : jpa.findByPatientIdAndStatus(patientId, status, page(page, size));
    return toPage(result);
  }

  @Override
  public PageResult<MedicalRecord> findByDoctor(
      UUID doctorId, RecordStatus status, int page, int size) {
    Page<MedicalRecordEntity> result =
        status == null
            ? jpa.findByDoctorId(doctorId, page(page, size))
            : jpa.findByDoctorIdAndStatus(doctorId, status, page(page, size));
    return toPage(result);
  }

  @Override
  public PageResult<MedicalRecord> findByPatientAndDoctor(
      UUID patientId, UUID doctorId, RecordStatus status, int page, int size) {
    Page<MedicalRecordEntity> result =
        status == null
            ? jpa.findByPatientIdAndDoctorId(patientId, doctorId, page(page, size))
            : jpa.findByPatientIdAndDoctorIdAndStatus(
                patientId, doctorId, status, page(page, size));
    return toPage(result);
  }

  @Override
  public int markDeletedByPatient(UUID patientId, Instant now) {
    return jpa.markDeletedByPatient(patientId, now);
  }

  @Override
  public java.util.List<UUID> findIdsByPatient(UUID patientId) {
    return jpa.findIdsByPatient(patientId);
  }

  @Override
  public java.util.List<UUID> findIdsByDoctor(UUID doctorId) {
    return jpa.findIdsByDoctor(doctorId);
  }

  @Override
  public java.util.List<UUID> findExpiredIds(java.time.Instant now, int limit) {
    return jpa.findExpiredIds(
        now, org.springframework.data.domain.PageRequest.of(0, Math.max(1, limit)));
  }

  @Override
  public void deleteById(UUID id) {
    jpa.deleteById(id);
  }

  @Override
  public int markDeletedByDoctor(UUID doctorId, Instant now) {
    return jpa.markDeletedByDoctor(doctorId, now);
  }

  private PageResult<MedicalRecord> toPage(Page<MedicalRecordEntity> result) {
    return new PageResult<>(
        result.getContent().stream().map(mapper::toDomain).toList(),
        result.getTotalElements(),
        result.getNumber(),
        result.getSize());
  }

  private static PageRequest page(int page, int size) {
    return PageRequest.of(
        Math.max(0, page), Math.max(1, size), Sort.by(Sort.Direction.DESC, "createdAt"));
  }
}
