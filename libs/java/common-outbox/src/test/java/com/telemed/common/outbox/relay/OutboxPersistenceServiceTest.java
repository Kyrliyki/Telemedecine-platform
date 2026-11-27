package com.telemed.common.outbox.relay;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.common.outbox.entity.OutboxEvent;
import com.telemed.common.outbox.port.OutboxRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxPersistenceServiceTest {

  @Mock private OutboxRepository repository;

  @Test
  void delegatesClaimSentAndFailed() {
    Instant now = Instant.now();
    Instant cutoff = now.minusSeconds(60);
    UUID eventId = UUID.randomUUID();
    OutboxEvent event = new com.telemed.common.outbox.entity.OutboxEvent() {};
    when(repository.claimPending("svc", 10, 5, now, cutoff)).thenReturn(List.of(event));
    OutboxPersistenceService service = new OutboxPersistenceService(repository);

    service.claimPending("svc", 10, 5, now, cutoff);
    service.markDeadStuck("svc", cutoff, 5);
    service.markSent(eventId);
    service.markFailed(eventId, "error", now, false);

    verify(repository).claimPending("svc", 10, 5, now, cutoff);
    verify(repository).markDeadStuck("svc", cutoff, 5);
    verify(repository).markSent(eventId);
    verify(repository).markFailed(eventId, "error", now, false);
  }
}
