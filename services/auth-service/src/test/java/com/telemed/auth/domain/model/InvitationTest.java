package com.telemed.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.auth.domain.valueobject.InvitationId;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class InvitationTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void invitationCanBeAcceptedOnce() {
    Invitation invitation =
        Invitation.create(UserId.newId(), new TokenHash("hash"), NOW, Duration.ofDays(1));

    invitation.accept(NOW.plusSeconds(10));

    assertThat(invitation.accepted()).isTrue();
    assertThat(invitation.acceptedAt()).isEqualTo(NOW.plusSeconds(10));
    assertThat(invitation.isUsable(NOW.plusSeconds(20))).isFalse();
    assertThatThrownBy(() -> invitation.accept(NOW.plusSeconds(20)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void expiredInvitationIsNotUsable() {
    Invitation invitation =
        Invitation.create(UserId.newId(), new TokenHash("hash"), NOW, Duration.ofHours(1));

    assertThat(invitation.isUsable(NOW.plus(Duration.ofHours(2)))).isFalse();
  }

  @Test
  void rehydratePreservesState() {
    InvitationId id = InvitationId.newId();
    UserId userId = UserId.newId();
    Instant acceptedAt = NOW.plusSeconds(5);

    Invitation invitation =
        Invitation.rehydrate(
            id, userId, new TokenHash("hash"), NOW, NOW.plusSeconds(60), true, acceptedAt);

    assertThat(invitation.id()).isEqualTo(id);
    assertThat(invitation.userId()).isEqualTo(userId);
    assertThat(invitation.accepted()).isTrue();
    assertThat(invitation.acceptedAt()).isEqualTo(acceptedAt);
  }
}
