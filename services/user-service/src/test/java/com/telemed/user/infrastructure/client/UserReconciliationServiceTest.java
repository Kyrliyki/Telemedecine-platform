package com.telemed.user.infrastructure.client;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.user.application.service.UserSyncService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserReconciliationServiceTest {

  @Test
  void walksAllPagesAndAppliesSnapshots() {
    AuthServiceClient client = mock(AuthServiceClient.class);
    UserSyncService syncService = mock(UserSyncService.class);
    AuthServiceProperties properties =
        new AuthServiceProperties(
            "http://auth-service:8080",
            "token",
            2,
            true,
            Duration.ofHours(1),
            Duration.ofSeconds(1));
    when(client.properties()).thenReturn(properties);

    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    UUID third = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-01T00:00:00Z");

    when(client.fetchPage(0, 2))
        .thenReturn(
            new AuthServiceClient.InternalUserPage(
                List.of(
                    new AuthServiceClient.InternalUser(
                        first, "a@example.com", true, "ACTIVE", Set.of("PATIENT"), now, now),
                    new AuthServiceClient.InternalUser(
                        second, "b@example.com", true, "BLOCKED", Set.of("DOCTOR"), now, now)),
                3,
                0,
                2,
                2));
    when(client.fetchPage(1, 2))
        .thenReturn(
            new AuthServiceClient.InternalUserPage(
                List.of(
                    new AuthServiceClient.InternalUser(
                        third, "c@example.com", true, "ACTIVE", Set.of("ADMIN"), now, now)),
                3,
                1,
                2,
                2));

    new UserReconciliationService(client, syncService).reconcile();

    verify(syncService).applySnapshot(first, "a@example.com", Set.of("PATIENT"), "ACTIVE", now);
    verify(syncService).applySnapshot(second, "b@example.com", Set.of("DOCTOR"), "BLOCKED", now);
    verify(syncService).applySnapshot(third, "c@example.com", Set.of("ADMIN"), "ACTIVE", now);
  }
}
