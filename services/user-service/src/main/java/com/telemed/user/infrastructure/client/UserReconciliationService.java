package com.telemed.user.infrastructure.client;

import com.telemed.user.application.service.UserSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class UserReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(UserReconciliationService.class);
  private static final int MAX_PAGES = 10_000;

  private final AuthServiceClient client;
  private final UserSyncService userSyncService;

  public UserReconciliationService(AuthServiceClient client, UserSyncService userSyncService) {
    this.client = client;
    this.userSyncService = userSyncService;
  }

  @Scheduled(
      initialDelayString = "${user.auth-service.reconcile-initial-delay:30000}",
      fixedDelayString = "${user.auth-service.reconcile-interval:3600000}")
  public void reconcile() {
    AuthServiceProperties properties = client.properties();
    if (!properties.reconciliationEnabled()) {
      return;
    }
    if (properties.internalToken().isBlank()) {
      log.warn("Skipping auth reconciliation: INTERNAL_SERVICE_TOKEN is not configured");
      return;
    }

    int page = 0;
    int synced = 0;
    try {
      while (page < MAX_PAGES) {
        AuthServiceClient.InternalUserPage response = client.fetchPage(page, properties.pageSize());
        if (response == null || response.content() == null || response.content().isEmpty()) {
          break;
        }
        for (AuthServiceClient.InternalUser user : response.content()) {
          try {
            userSyncService.applySnapshot(
                user.userId(), user.email(), user.roles(), user.status(), user.updatedAt());
            synced++;
          } catch (Exception exception) {
            log.warn("Failed to reconcile profile userId={}", user.userId(), exception);
          }
        }
        page++;
        if (page >= response.totalPages()) {
          break;
        }
      }
      log.info("Auth reconciliation completed: profiles={}, pages={}", synced, page);
    } catch (Exception exception) {
      log.error("Auth reconciliation failed on page {}", page, exception);
    }
  }
}
