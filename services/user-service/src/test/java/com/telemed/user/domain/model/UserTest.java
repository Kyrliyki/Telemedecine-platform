package com.telemed.user.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UserTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void createsProfileFromAuthIdentity() {
    User user =
        User.fromAuth(
            UserId.newId(), new Email("Patient@Example.COM"), Set.of(UserRole.PATIENT), NOW);

    assertThat(user.getEmail().value()).isEqualTo("patient@example.com");
    assertThat(user.getRoles()).containsExactly(UserRole.PATIENT);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getFirstName()).isEmpty();
    assertThat(user.getLastName()).isEmpty();
    assertThat(user.displayName()).isEqualTo("patient@example.com");
  }

  @Test
  void defaultsToDefaultRoleWhenRolesAreEmpty() {
    User user = User.fromAuth(UserId.newId(), new Email("a@b.co"), Set.of(), NOW);

    assertThat(user.getRoles()).containsExactly(UserRole.DEFAULT);
  }

  @Test
  void updatesProfileNamesWithoutChangingRolesOrStatus() {
    User user = User.fromAuth(UserId.newId(), new Email("a@b.co"), Set.of(UserRole.DOCTOR), NOW);

    user.updateProfile("  Ivan ", " Petrov ", NOW.plusSeconds(10));

    assertThat(user.getFirstName()).isEqualTo("Ivan");
    assertThat(user.getLastName()).isEqualTo("Petrov");
    assertThat(user.displayName()).isEqualTo("Ivan Petrov");
    assertThat(user.getRoles()).containsExactly(UserRole.DOCTOR);
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(user.getUpdatedAt()).isEqualTo(NOW.plusSeconds(10));
  }

  @Test
  void blocksAndUnblocksProfile() {
    User user = User.fromAuth(UserId.newId(), new Email("a@b.co"), Set.of(UserRole.PATIENT), NOW);

    user.block(NOW.plusSeconds(1));
    assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);

    user.unblock(NOW.plusSeconds(2));
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void rejectsUpdateTimeBeforeLastUpdate() {
    User user = User.fromAuth(UserId.newId(), new Email("a@b.co"), Set.of(UserRole.PATIENT), NOW);

    assertThatThrownBy(() -> user.updateProfile("A", "B", NOW.minusSeconds(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
