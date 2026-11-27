package com.telemed.schedule.infrastructure.persistence.adapter;

import com.telemed.schedule.infrastructure.persistence.repository.JpaProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventRepositoryAdapter {

  private final JpaProcessedEventRepository jpa;

  public ProcessedEventRepositoryAdapter(JpaProcessedEventRepository jpa) {
    this.jpa = jpa;
  }

  public boolean tryClaim(UUID eventId, String eventType, Instant now) {
    return jpa.tryClaim(eventId, eventType, now) == 1;
  }
}
