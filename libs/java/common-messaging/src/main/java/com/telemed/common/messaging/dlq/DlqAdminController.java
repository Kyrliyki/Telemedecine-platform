package com.telemed.common.messaging.dlq;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-local DLQ operations. The public operator API is aggregated by the API Gateway at {@code
 * /api/v1/admin/dlq}.
 */
@RestController
@RequestMapping("/internal/dlq")
public class DlqAdminController {

  private final DlqAdminService service;

  public DlqAdminController(DlqAdminService service) {
    this.service = service;
  }

  @GetMapping
  public List<DlqQueueView> list() {
    return service.list();
  }

  @PostMapping("/{key}/replay")
  public DlqReplayResult replay(
      @PathVariable String key,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "false") boolean dryRun) {
    return service.replay(key, limit, dryRun);
  }

  @PostMapping("/{key}/purge")
  public DlqPurgeResult purge(@PathVariable String key) {
    return service.purge(key);
  }
}
