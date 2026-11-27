package com.telemed.user.infrastructure.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.ProcessedEventRepository;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProcessedEventCleanupJobTest {

  @Test
  void deletesRowsOlderThanRetention() {
    ProcessedEventRepository repository = mock(ProcessedEventRepository.class);
    Clock clock = mock(Clock.class);
    Instant now = Instant.parse("2026-01-31T03:00:00Z");
    when(clock.now()).thenReturn(now);

    new ProcessedEventCleanupJob(repository, clock, Duration.ofDays(30)).cleanup();

    verify(repository).deleteProcessedBefore(now.minus(Duration.ofDays(30)));
  }
}
