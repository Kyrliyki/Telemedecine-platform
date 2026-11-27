package com.telemed.emr.infrastructure.persistence.repository;

import com.telemed.emr.infrastructure.persistence.entity.ProcessedEventEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaProcessedEventRepository extends JpaRepository<ProcessedEventEntity, UUID> {

  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query(
      value =
          """
          INSERT INTO processed_event (event_id, event_type, processed_at)
          VALUES (:eventId, :eventType, :processedAt)
          ON CONFLICT (event_id) DO NOTHING
          """,
      nativeQuery = true)
  int tryClaim(
      @Param("eventId") UUID eventId,
      @Param("eventType") String eventType,
      @Param("processedAt") Instant processedAt);

  long deleteByProcessedAtBefore(Instant cutoff);
}
