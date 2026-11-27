package com.telemed.gateway.infrastructure.dlq;

import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqOverview;
import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqPurgeResponse;
import com.telemed.gateway.infrastructure.dlq.DlqViews.DlqReplayResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/admin/dlq")
@PreAuthorize("hasRole('ADMIN')")
public class DlqAdminController {

  private final DlqAggregatorService aggregator;

  public DlqAdminController(DlqAggregatorService aggregator) {
    this.aggregator = aggregator;
  }

  @GetMapping
  public Mono<DlqOverview> overview() {
    return aggregator.overview();
  }

  @PostMapping("/{service}/{queue}/replay")
  public Mono<DlqReplayResponse> replay(
      @PathVariable String service,
      @PathVariable String queue,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "false") boolean dryRun) {
    return aggregator.replay(service, queue, limit, dryRun);
  }

  @PostMapping("/{service}/{queue}/purge")
  public Mono<DlqPurgeResponse> purge(@PathVariable String service, @PathVariable String queue) {
    return aggregator.purge(service, queue);
  }
}
