package com.telemed.emr.infrastructure.persistence.mapper;

import com.telemed.emr.domain.model.VitalSigns;
import com.telemed.emr.infrastructure.persistence.entity.VitalSignsEntity;
import org.springframework.stereotype.Component;

@Component
public class VitalSignsEntityMapper {

  public VitalSigns toDomain(VitalSignsEntity entity) {
    return new VitalSigns(
        entity.getId(),
        entity.getRecordId(),
        entity.getHeightCm(),
        entity.getWeightKg(),
        entity.getSystolic(),
        entity.getDiastolic(),
        entity.getPulse(),
        entity.getTemperatureC(),
        entity.getSpo2(),
        entity.getRecordedAt());
  }

  public VitalSignsEntity toEntity(VitalSigns signs) {
    VitalSignsEntity entity = new VitalSignsEntity();
    entity.setId(signs.id());
    entity.setRecordId(signs.recordId());
    entity.setHeightCm(signs.heightCm());
    entity.setWeightKg(signs.weightKg());
    entity.setSystolic(signs.systolic());
    entity.setDiastolic(signs.diastolic());
    entity.setPulse(signs.pulse());
    entity.setTemperatureC(signs.temperatureC());
    entity.setSpo2(signs.spo2());
    entity.setRecordedAt(signs.recordedAt());
    return entity;
  }
}
