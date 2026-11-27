package com.telemed.user.presentation.rest.v1.controller;

import com.telemed.user.infrastructure.messaging.DlqReplayService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users/events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEventController {

  private final DlqReplayService dlqReplayService;

  public AdminEventController(DlqReplayService dlqReplayService) {
    this.dlqReplayService = dlqReplayService;
  }

  @PostMapping("/replay-dlq")
  public ReplayResponse replayDlq(@RequestParam(defaultValue = "100") int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 1000));
    DlqReplayService.ReplayResult result = dlqReplayService.replay(safeLimit);
    return new ReplayResponse(result.replayed(), result.failed());
  }

  public record ReplayResponse(int replayed, int failed) {}
}
