package com.telemed.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.domain.valueobject.UserId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void rotationRevokesOldTokenAndKeepsFamily() {
    RefreshToken original =
        RefreshToken.issue(UserId.newId(), new TokenHash("old"), NOW, Duration.ofDays(30));

    RefreshToken rotated =
        original.rotate(new TokenHash("new"), NOW.plusSeconds(60), Duration.ofDays(30));

    assertThat(original.revoked()).isTrue();
    assertThat(original.revokedAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(rotated.revoked()).isFalse();
    assertThat(rotated.familyId()).isEqualTo(original.familyId());
    assertThat(rotated.tokenHash()).isEqualTo(new TokenHash("new"));
  }

  @Test
  void inactiveTokenCannotBeRotated() {
    RefreshToken original =
        RefreshToken.issue(UserId.newId(), new TokenHash("old"), NOW, Duration.ofHours(1));
    original.revoke(NOW.plusSeconds(1));

    assertThatThrownBy(
            () -> original.rotate(new TokenHash("new"), NOW.plusSeconds(2), Duration.ofHours(1)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void tokenExpiresAtItsTtl() {
    RefreshToken token =
        RefreshToken.issue(UserId.newId(), new TokenHash("hash"), NOW, Duration.ofMinutes(5));

    assertThat(token.isActive(NOW.plusSeconds(299))).isTrue();
    assertThat(token.isActive(NOW.plusSeconds(301))).isFalse();
  }
}
