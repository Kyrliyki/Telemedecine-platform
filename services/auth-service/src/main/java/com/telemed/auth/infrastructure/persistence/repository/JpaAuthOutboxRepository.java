package com.telemed.auth.infrastructure.persistence.repository;

import com.telemed.auth.infrastructure.persistence.entity.AuthOutboxEvent;
import com.telemed.common.outbox.entity.OutboxStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaAuthOutboxRepository extends JpaRepository<AuthOutboxEvent, UUID> {

  @Query(
      value =
          """
            WITH candidates AS (
                SELECT id, status, retry_count
                FROM outbox_event
                WHERE service = :service
                  AND (
                     (status IN (:statuses)
                      AND retry_count < :maxAttempts
                      AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                    OR
                      (status = 'PROCESSING'
                       AND processing_started_at <= :cutoff
                       AND retry_count + 1 < :maxAttempts)
                  )
              ORDER BY created_at ASC
              LIMIT :batchSize
              FOR UPDATE SKIP LOCKED
        )
        UPDATE outbox_event e
                SET status = 'PROCESSING',
                    processing_started_at = :now,
                    retry_count = CASE
                        WHEN c.status = 'PROCESSING' THEN e.retry_count + 1
                        ELSE e.retry_count
                    END,
                    last_error = CASE
                        WHEN c.status = 'PROCESSING' THEN 'Processing timeout, re-claimed'
                        ELSE e.last_error
                    END
        FROM candidates c
        WHERE e.id = c.id
        RETURNING e.*
        """,
      nativeQuery = true)
  List<AuthOutboxEvent> claimPending(
      @Param("service") String service,
      @Param("statuses") Collection<String> statuses,
      @Param("maxAttempts") int maxAttempts,
      @Param("now") Instant now,
      @Param("cutoff") Instant cutoff,
      @Param("batchSize") int batchSize);

  @Modifying
  @Query(
      value =
          """
            UPDATE outbox_event
            SET status = 'DEAD',
                last_error = 'Processing timeout, max attempts exhausted',
                processing_started_at = NULL
            WHERE service = :service
              AND status = 'PROCESSING'
              AND processing_started_at <= :cutoff
              AND retry_count + 1 >= :maxAttempts
            """,
      nativeQuery = true)
  int markDeadStuck(
      @Param("service") String service,
      @Param("cutoff") Instant cutoff,
      @Param("maxAttempts") int maxAttempts);

  @Modifying
  @Query(
      """
            UPDATE AuthOutboxEvent e
            SET e.status = :sentStatus, e.sentAt = :now, e.processingStartedAt = NULL
            WHERE e.eventId = :eventId AND e.status = :processingStatus
            """)
  void markSent(
      @Param("eventId") UUID eventId,
      @Param("now") Instant now,
      @Param("sentStatus") OutboxStatus sentStatus,
      @Param("processingStatus") OutboxStatus processingStatus);

  @Modifying
  @Query(
      """
            UPDATE AuthOutboxEvent e
            SET e.status = :status,
                e.retryCount = e.retryCount + 1,
                e.lastError = :reason,
                e.nextAttemptAt = :nextAttemptAt,
                e.processingStartedAt = NULL
            WHERE e.eventId = :eventId AND e.status = :processingStatus
            """)
  void markFailed(
      @Param("eventId") UUID eventId,
      @Param("reason") String reason,
      @Param("nextAttemptAt") Instant nextAttemptAt,
      @Param("status") OutboxStatus status,
      @Param("processingStatus") OutboxStatus processingStatus);

  @Modifying
  @Query(
      "DELETE FROM AuthOutboxEvent e WHERE e.service = :service AND e.status = :status AND e.createdAt < :cutoff")
  int deleteBefore(
      @Param("service") String service,
      @Param("status") OutboxStatus status,
      @Param("cutoff") Instant cutoff);
}
