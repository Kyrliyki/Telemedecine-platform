package com.telemed.schedule.presentation.rest.v1.controller;

import com.telemed.schedule.infrastructure.messaging.ScheduleDlqReplayService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/schedule/events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminScheduleEventController {

  private final ScheduleDlqReplayService replayService;

  public AdminScheduleEventController(ScheduleDlqReplayService replayService) {
    this.replayService = replayService;
  }

  @PostMapping("/replay-dlq")
  public ReplayResponse replay(
      @RequestParam(defaultValue = "auth") String queue,
      @RequestParam(defaultValue = "100") int limit) {
    int safeLimit = Math.max(1, Math.min(limit, 1000));
    var result = replayService.replay(queue, safeLimit);
    return new ReplayResponse(result.replayed(), result.failed());
  }

  public record ReplayResponse(int replayed, int failed) {}
}
