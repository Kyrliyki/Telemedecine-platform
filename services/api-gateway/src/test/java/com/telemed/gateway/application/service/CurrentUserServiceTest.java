package com.telemed.gateway.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.telemed.gateway.application.port.output.CurrentUserReader;
import com.telemed.gateway.domain.model.GatewayUser;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class CurrentUserServiceTest {

  @Test
  void delegatesToCurrentUserReader() {
    CurrentUserReader reader = mock(CurrentUserReader.class);
    GatewayUser user =
        new GatewayUser(
            "user-1",
            "user@example.com",
            Set.of("DOCTOR"),
            "jti-1",
            Instant.parse("2026-01-01T11:00:00Z"));
    when(reader.read()).thenReturn(Mono.just(user));

    CurrentUserService service = new CurrentUserService(reader);

    assertThat(service.currentUser().block()).isEqualTo(user);
  }
}
