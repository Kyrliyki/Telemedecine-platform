package com.telemed.common.outbox.cleanup;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.common.outbox.config.OutboxProperties;
import com.telemed.common.outbox.port.OutboxRepository;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxCleanupJobTest {

  @Mock private OutboxRepository repository;

  @Test
  void deletesSentAndDeadRowsOlderThanRetention() {
    OutboxProperties properties =
        new OutboxProperties(
            10,
            Duration.ofDays(7),
            5,
            Duration.ofSeconds(1),
            Duration.ofSeconds(10),
            Duration.ofMinutes(5));
    when(repository.deleteSentBefore(eq("svc"), any())).thenReturn(2);
    when(repository.deleteDeadBefore(eq("svc"), any())).thenReturn(1);

    new OutboxCleanupJob(repository, properties, "svc").cleanup();

    verify(repository).deleteSentBefore(eq("svc"), any());
    verify(repository).deleteDeadBefore(eq("svc"), any());
  }
}
