package com.telemed.schedule.infrastructure.persistence.mapper;

import com.telemed.schedule.domain.model.WorkingHours;
import com.telemed.schedule.infrastructure.persistence.entity.WorkingHoursEntity;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

@Component
public class WorkingHoursEntityMapper {

  public WorkingHours toDomain(WorkingHoursEntity entity) {
    return WorkingHours.rehydrate(
        entity.getId(),
        entity.getDoctorId(),
        entity.getDayOfWeek(),
        entity.getStartTime(),
        entity.getEndTime(),
        entity.getSlotDurationMinutes(),
        entity.getBufferMinutes(),
        ZoneId.of(entity.getTimezone()),
        entity.getEffectiveFrom(),
        entity.getEffectiveTo(),
        entity.getSlotType(),
        entity.getLocation(),
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  public WorkingHoursEntity toEntity(WorkingHours hours) {
    WorkingHoursEntity entity = new WorkingHoursEntity();
    entity.setId(hours.getId());
    entity.setDoctorId(hours.getDoctorId());
    entity.setDayOfWeek(hours.getDayOfWeek());
    entity.setStartTime(hours.getStartTime());
    entity.setEndTime(hours.getEndTime());
    entity.setSlotDurationMinutes(hours.getSlotDurationMinutes());
    entity.setBufferMinutes(hours.getBufferMinutes());
    entity.setTimezone(hours.getTimezone().getId());
    entity.setEffectiveFrom(hours.getEffectiveFrom());
    entity.setEffectiveTo(hours.getEffectiveTo());
    entity.setSlotType(hours.getSlotType());
    entity.setLocation(hours.getLocation());
    entity.setCreatedAt(hours.getCreatedAt());
    entity.setUpdatedAt(hours.getUpdatedAt());
    return entity;
  }
}
