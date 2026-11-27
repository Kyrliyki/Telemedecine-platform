package com.telemed.gateway.infrastructure.dlq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DlqAdminControllerTest {

  @Test
  void delegatesOverviewAndReplay() {
    DlqAggregatorService aggregator = mock(DlqAggregatorService.class);
    DlqAdminController controller = new DlqAdminController(aggregator);
    var overview = new DlqViews.DlqOverview(Instant.now(), List.of());
    when(aggregator.overview()).thenReturn(Mono.just(overview));
    when(aggregator.replay("user-service", "auth", 10, true))
        .thenReturn(
            Mono.just(new DlqViews.DlqReplayResponse("user-service", "auth", 0, 0, true, 2)));

    assertThat(controller.overview().block()).isSameAs(overview);
    assertThat(controller.replay("user-service", "auth", 10, true).block().candidates())
        .isEqualTo(2);
  }
}
