package com.telemed.schedule.infrastructure.persistence.mapper;

import com.telemed.schedule.domain.model.TimeSlot;
import com.telemed.schedule.infrastructure.persistence.entity.TimeSlotEntity;
import org.springframework.stereotype.Component;

@Component
public class TimeSlotEntityMapper {

  public TimeSlot toDomain(TimeSlotEntity entity) {
    return TimeSlot.rehydrate(
        entity.getId(),
        entity.getDoctorId(),
        entity.getStartsAt(),
        entity.getEndsAt(),
        entity.getStatus(),
        entity.getAppointmentId(),
        entity.getHoldExpiresAt(),
        entity.getSlotType(),
        entity.getLocation(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getVersion());
  }

  public TimeSlotEntity toEntity(TimeSlot slot) {
    TimeSlotEntity entity = new TimeSlotEntity();
    entity.setId(slot.getId());
    entity.setDoctorId(slot.getDoctorId());
    entity.setStartsAt(slot.getStartsAt());
    entity.setEndsAt(slot.getEndsAt());
    entity.setStatus(slot.getStatus());
    entity.setAppointmentId(slot.getAppointmentId());
    entity.setHoldExpiresAt(slot.getHoldExpiresAt());
    entity.setSlotType(slot.getSlotType());
    entity.setLocation(slot.getLocation());
    entity.setCreatedAt(slot.getCreatedAt());
    entity.setUpdatedAt(slot.getUpdatedAt());
    entity.setVersion(slot.getVersion());
    return entity;
  }
}
