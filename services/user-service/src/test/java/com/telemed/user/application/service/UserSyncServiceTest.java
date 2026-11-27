package com.telemed.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.common.events.AuthEventPayloads.UserBlockedPayload;
import com.telemed.common.events.AuthEventPayloads.UserCreatedByAdminPayload;
import com.telemed.common.events.AuthEventPayloads.UserEmailChangedPayload;
import com.telemed.common.events.AuthEventPayloads.UserRegisteredPayload;
import com.telemed.common.events.AuthEventPayloads.UserRoleChangedPayload;
import com.telemed.common.events.AuthEventPayloads.UserUnblockedPayload;
import com.telemed.user.application.exception.EmailAlreadyInUseException;
import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.DoctorProfileRepository;
import com.telemed.user.application.port.output.UserRepository;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import com.telemed.user.infrastructure.audit.UserAuditLogger;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserSyncServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Mock private UserRepository userRepository;

  @Mock private DoctorProfileRepository doctorProfileRepository;

  @Mock private Clock clock;

  @Mock private UserAuditLogger audit;

  private UserSyncService service;

  @BeforeEach
  void setUp() {
    service = new UserSyncService(userRepository, doctorProfileRepository, clock, audit);
  }

  @Test
  void createsProfileFromRegisteredEvent() {
    UUID userId = UUID.randomUUID();
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.empty());
    when(userRepository.findByEmail(new Email("new@example.com"))).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onUserRegistered(
        new UserRegisteredPayload(
            userId, "New@Example.com", List.of("PATIENT"), false, "PASSWORD"));

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    User saved = captor.getValue();
    assertThat(saved.getId().value()).isEqualTo(userId);
    assertThat(saved.getEmail().value()).isEqualTo("new@example.com");
    assertThat(saved.getRoles()).containsExactly(UserRole.PATIENT);
    assertThat(saved.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void updatesExistingProfileAndPreservesNames() {
    UUID userId = UUID.randomUUID();
    User existing =
        User.fromAuth(
            new UserId(userId),
            new Email("old@example.com"),
            Set.of(UserRole.PATIENT),
            NOW.minusSeconds(60));
    existing.updateProfile("Ivan", "Petrov", NOW.minusSeconds(50));

    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(existing));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onUserCreatedByAdmin(
        new UserCreatedByAdminPayload(userId, "new@example.com", List.of("DOCTOR"), null));

    assertThat(existing.getEmail().value()).isEqualTo("new@example.com");
    assertThat(existing.getRoles()).containsExactly(UserRole.DOCTOR);
    assertThat(existing.getFirstName()).isEqualTo("Ivan");
    assertThat(existing.getLastName()).isEqualTo("Petrov");
    verify(userRepository).save(existing);
  }

  @Test
  void replacesRolesOnRoleChanged() {
    UUID userId = UUID.randomUUID();
    User user =
        User.fromAuth(
            new UserId(userId),
            new Email("a@example.com"),
            Set.of(UserRole.PATIENT),
            NOW.minusSeconds(60));
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onRoleChanged(
        new UserRoleChangedPayload(userId, List.of("DOCTOR", "ADMIN"), List.of("PATIENT")));

    assertThat(user.getRoles()).containsExactlyInAnyOrder(UserRole.DOCTOR, UserRole.ADMIN);
    verify(userRepository).save(user);
  }

  @Test
  void removesDoctorProfileWhenDoctorRoleIsRevoked() {
    UUID userId = UUID.randomUUID();
    User user =
        User.fromAuth(
            new UserId(userId),
            new Email("doctor@example.com"),
            Set.of(UserRole.DOCTOR),
            NOW.minusSeconds(60));
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onRoleChanged(
        new UserRoleChangedPayload(userId, List.of("PATIENT"), List.of("DOCTOR")));

    assertThat(user.getRoles()).containsExactly(UserRole.PATIENT);
    verify(doctorProfileRepository).deleteByUserId(new UserId(userId));
  }

  @Test
  void updatesEmailOnEmailChanged() {
    UUID userId = UUID.randomUUID();
    User user =
        User.fromAuth(
            new UserId(userId),
            new Email("old@example.com"),
            Set.of(UserRole.PATIENT),
            NOW.minusSeconds(60));
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(user));
    when(userRepository.findByEmail(new Email("new@example.com"))).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onEmailChanged(
        new UserEmailChangedPayload(userId, "old@example.com", "new@example.com"));

    assertThat(user.getEmail().value()).isEqualTo("new@example.com");
  }

  @Test
  void rejectsEmailOwnedByAnotherProfile() {
    UUID userId = UUID.randomUUID();
    User user =
        User.fromAuth(
            new UserId(userId),
            new Email("old@example.com"),
            Set.of(UserRole.PATIENT),
            NOW.minusSeconds(60));
    User other =
        User.fromAuth(
            UserId.newId(),
            new Email("taken@example.com"),
            Set.of(UserRole.PATIENT),
            NOW.minusSeconds(60));
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(user));
    when(userRepository.findByEmail(new Email("taken@example.com"))).thenReturn(Optional.of(other));

    assertThatThrownBy(
            () ->
                service.onEmailChanged(
                    new UserEmailChangedPayload(userId, "old@example.com", "taken@example.com")))
        .isInstanceOf(EmailAlreadyInUseException.class);
    verify(userRepository, never()).save(any());
  }

  @Test
  void blocksAndUnblocksProfile() {
    UUID userId = UUID.randomUUID();
    User user =
        User.fromAuth(
            new UserId(userId),
            new Email("a@example.com"),
            Set.of(UserRole.PATIENT),
            NOW.minusSeconds(60));
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.onBlocked(new UserBlockedPayload(userId, "spam"));
    assertThat(user.getStatus()).isEqualTo(UserStatus.BLOCKED);

    service.onUnblocked(new UserUnblockedPayload(userId));
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void snapshotCreatesMissingProfileAndAppliesStatus() {
    UUID userId = UUID.randomUUID();
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.applySnapshot(userId, "blocked@example.com", Set.of("PATIENT"), "BLOCKED", NOW);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(UserStatus.BLOCKED);
    assertThat(captor.getValue().getRoles()).containsExactly(UserRole.PATIENT);
  }

  @Test
  void snapshotUpdatesExistingProfileAndPreservesNames() {
    UUID userId = UUID.randomUUID();
    User user =
        User.fromAuth(
            new UserId(userId),
            new Email("old@example.com"),
            Set.of(UserRole.PATIENT),
            NOW.minusSeconds(60));
    user.updateProfile("Ivan", "Petrov", NOW.minusSeconds(50));
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(user));
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    service.applySnapshot(userId, "new@example.com", Set.of("DOCTOR"), "ACTIVE", NOW);

    assertThat(user.getEmail().value()).isEqualTo("new@example.com");
    assertThat(user.getRoles()).containsExactly(UserRole.DOCTOR);
    assertThat(user.getFirstName()).isEqualTo("Ivan");
    assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
  }

  @Test
  void snapshotDoesNotWriteWhenAuthStateIsUnchanged() {
    UUID userId = UUID.randomUUID();
    User user =
        User.fromAuth(
            new UserId(userId), new Email("same@example.com"), Set.of(UserRole.PATIENT), NOW);
    when(clock.now()).thenReturn(NOW);
    when(userRepository.findById(new UserId(userId))).thenReturn(Optional.of(user));

    service.applySnapshot(userId, "same@example.com", Set.of("PATIENT"), "ACTIVE", NOW);

    verify(userRepository, never()).save(any());
    verify(doctorProfileRepository, never()).deleteByUserId(any());
  }
}
