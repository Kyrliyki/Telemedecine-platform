package com.telemed.emr.infrastructure.persistence.adapter;

import com.telemed.emr.application.port.output.VitalSignsRepository;
import com.telemed.emr.domain.model.VitalSigns;
import com.telemed.emr.infrastructure.persistence.entity.VitalSignsEntity;
import com.telemed.emr.infrastructure.persistence.mapper.VitalSignsEntityMapper;
import com.telemed.emr.infrastructure.persistence.repository.JpaVitalSignsRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class VitalSignsRepositoryAdapter implements VitalSignsRepository {

  private final JpaVitalSignsRepository jpa;
  private final VitalSignsEntityMapper mapper;

  public VitalSignsRepositoryAdapter(JpaVitalSignsRepository jpa, VitalSignsEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public Optional<VitalSigns> findByRecordId(UUID recordId) {
    return jpa.findByRecordId(recordId).map(mapper::toDomain);
  }

  @Override
  public VitalSigns save(VitalSigns signs) {
    VitalSignsEntity entity =
        jpa.findByRecordId(signs.recordId())
            .orElseGet(
                () -> {
                  VitalSignsEntity created = mapper.toEntity(signs);
                  created.setId(signs.id());
                  return created;
                });
    entity.setHeightCm(signs.heightCm());
    entity.setWeightKg(signs.weightKg());
    entity.setSystolic(signs.systolic());
    entity.setDiastolic(signs.diastolic());
    entity.setPulse(signs.pulse());
    entity.setTemperatureC(signs.temperatureC());
    entity.setSpo2(signs.spo2());
    entity.setRecordedAt(signs.recordedAt());
    return mapper.toDomain(jpa.save(entity));
  }

  @Override
  public void deleteByRecordId(UUID recordId) {
    jpa.deleteByRecordId(recordId);
  }
}
