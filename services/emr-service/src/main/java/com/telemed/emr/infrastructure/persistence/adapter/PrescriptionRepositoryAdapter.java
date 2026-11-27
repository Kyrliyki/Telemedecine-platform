package com.telemed.emr.infrastructure.persistence.adapter;

import com.telemed.emr.application.port.output.PrescriptionRepository;
import com.telemed.emr.domain.model.Prescription;
import com.telemed.emr.infrastructure.persistence.entity.PrescriptionEntity;
import com.telemed.emr.infrastructure.persistence.mapper.PrescriptionEntityMapper;
import com.telemed.emr.infrastructure.persistence.repository.JpaPrescriptionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PrescriptionRepositoryAdapter implements PrescriptionRepository {

  private final JpaPrescriptionRepository jpa;
  private final PrescriptionEntityMapper mapper;

  public PrescriptionRepositoryAdapter(
      JpaPrescriptionRepository jpa, PrescriptionEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public List<Prescription> findByRecordId(UUID recordId) {
    return jpa.findAllByRecordIdOrderByCreatedAtAsc(recordId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional
  public List<Prescription> replaceForRecord(UUID recordId, List<Prescription> prescriptions) {
    jpa.deleteAllByRecordId(recordId);
    jpa.flush();
    List<PrescriptionEntity> saved =
        jpa.saveAll(prescriptions.stream().map(mapper::toEntity).toList());
    return saved.stream().map(mapper::toDomain).toList();
  }

  @Override
  public void deleteByRecordId(UUID recordId) {
    jpa.deleteAllByRecordId(recordId);
  }
}
