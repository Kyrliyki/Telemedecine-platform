package com.telemed.schedule.infrastructure.persistence.mapper;

import com.telemed.schedule.domain.model.ScheduleException;
import com.telemed.schedule.domain.valueobject.ExceptionRecurrence;
import com.telemed.schedule.infrastructure.persistence.entity.ScheduleExceptionEntity;
import org.springframework.stereotype.Component;

@Component
public class ScheduleExceptionEntityMapper {

  public ScheduleException toDomain(ScheduleExceptionEntity entity) {
    return ScheduleException.rehydrate(
        entity.getId(),
        entity.getDoctorId(),
        entity.getStartsAt(),
        entity.getEndsAt(),
        entity.getReason(),
        ExceptionRecurrence.valueOf(entity.getRecurrence()),
        entity.getRecurrenceUntil(),
        entity.getCreatedAt());
  }

  public ScheduleExceptionEntity toEntity(ScheduleException exception) {
    ScheduleExceptionEntity entity = new ScheduleExceptionEntity();
    entity.setId(exception.getId());
    entity.setDoctorId(exception.getDoctorId());
    entity.setStartsAt(exception.getStartsAt());
    entity.setEndsAt(exception.getEndsAt());
    entity.setReason(exception.getReason());
    entity.setRecurrence(exception.getRecurrence().name());
    entity.setRecurrenceUntil(exception.getRecurrenceUntil());
    entity.setCreatedAt(exception.getCreatedAt());
    return entity;
  }
}
