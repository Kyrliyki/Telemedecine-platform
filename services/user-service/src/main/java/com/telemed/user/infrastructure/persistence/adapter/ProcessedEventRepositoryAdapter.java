package com.telemed.user.infrastructure.persistence.adapter;

import com.telemed.user.application.port.output.ProcessedEventRepository;
import com.telemed.user.infrastructure.persistence.repository.JpaProcessedEventRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProcessedEventRepositoryAdapter implements ProcessedEventRepository {

  private final JpaProcessedEventRepository jpa;

  public ProcessedEventRepositoryAdapter(JpaProcessedEventRepository jpa) {
    this.jpa = jpa;
  }

  @Override
  public boolean tryClaim(UUID eventId, String eventType, Instant claimedAt) {
    return jpa.tryClaim(eventId, eventType, claimedAt) == 1;
  }

  @Override
  public int deleteProcessedBefore(Instant cutoff) {
    return Math.toIntExact(jpa.deleteByProcessedAtBefore(cutoff));
  }
}
