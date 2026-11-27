package com.telemed.auth.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.auth.domain.event.UserEmailVerifiedEvent;
import com.telemed.auth.domain.event.UserRegisteredEvent;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.PasswordHash;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuthUserTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
  private static final Email EMAIL = new Email("patient@example.com");
  private static final PasswordHash PASSWORD = new PasswordHash("$2a$12$hash");

  @Test
  void registeredUserCanVerifyEmailAndBecomeActive() {
    AuthUser user = AuthUser.registerWithPassword(EMAIL, PASSWORD, NOW);

    assertThat(user.status().name()).isEqualTo("PENDING_EMAIL_VERIFICATION");
    assertThat(user.emailVerified()).isFalse();

    user.verifyEmail(NOW);
    user.activate(NOW);

    assertThat(user.emailVerified()).isTrue();
    assertThat(user.status().name()).isEqualTo("ACTIVE");
    assertThat(user.pullEvents())
        .hasAtLeastOneElementOfType(UserRegisteredEvent.class)
        .hasAtLeastOneElementOfType(UserEmailVerifiedEvent.class);
  }

  @Test
  void lastRoleCannotBeRemoved() {
    AuthUser user = AuthUser.registerWithPassword(EMAIL, PASSWORD, NOW);

    assertThatThrownBy(
            () ->
                user.removeRole(
                    UserRole.PATIENT, Actor.user(UserId.newId(), Set.of(UserRole.ADMIN)), NOW))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("at least one role");
  }

  @Test
  void invitedUserCanAcceptInvitation() {
    Actor admin = Actor.user(UserId.newId(), Set.of(UserRole.ADMIN));
    AuthUser user =
        AuthUser.invite(EMAIL, Set.of(UserRole.DOCTOR), NOW.plus(Duration.ofDays(7)), admin, NOW);

    user.acceptInvitation(PASSWORD, NOW);

    assertThat(user.status().name()).isEqualTo("ACTIVE");
    assertThat(user.emailVerified()).isTrue();
    assertThat(user.findLocalIdentity()).isPresent();
    assertThat(user.roles()).containsExactly(UserRole.DOCTOR);
  }

  @Test
  void blockedUserCannotAuthenticate() {
    AuthUser user = AuthUser.registerWithPassword(EMAIL, PASSWORD, NOW);
    user.verifyEmail(NOW);
    user.activate(NOW);

    user.block("policy violation", Actor.systemActor(), NOW);

    assertThat(user.status().name()).isEqualTo("BLOCKED");
    assertThatThrownBy(user::ensureCanAuthenticate)
        .isInstanceOf(com.telemed.auth.domain.exception.UserBlockedException.class);
  }

  @Test
  void updatingWithEarlierInstantIsRejected() {
    AuthUser user = AuthUser.registerWithPassword(EMAIL, PASSWORD, NOW);

    assertThatThrownBy(() -> user.verifyEmail(NOW.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
