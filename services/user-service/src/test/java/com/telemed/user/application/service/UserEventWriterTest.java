package com.telemed.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.telemed.common.outbox.entity.OutboxEvent;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.infrastructure.persistence.entity.UserOutboxEvent;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class UserEventWriterTest {

  @Mock private OutboxRepository outboxRepository;

  @Test
  void writesProfileUpdatedEventToOutbox() {
    User user =
        User.fromAuth(
            UserId.newId(),
            new Email("doctor@example.com"),
            Set.of(UserRole.DOCTOR),
            Instant.parse("2026-01-01T10:00:00Z"));
    user.updateProfile("Ivan", "Petrov", Instant.parse("2026-01-01T10:01:00Z"));
    UserEventWriter writer =
        new UserEventWriter(
            outboxRepository,
            new OutboxEventFactory("user-service", UserOutboxEvent::new),
            JsonMapper.builder().build());

    writer.profileUpdated(user, null, Instant.parse("2026-01-01T10:01:00Z"));

    ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
    verify(outboxRepository).save(captor.capture());
    assertThat(captor.getValue().getEventType()).isEqualTo("user.profile.updated.v1");
    assertThat(captor.getValue().getPayload())
        .contains("\"displayName\":\"Ivan Petrov\"")
        .contains("\"userId\":\"" + user.getId().value() + "\"");
  }
}
