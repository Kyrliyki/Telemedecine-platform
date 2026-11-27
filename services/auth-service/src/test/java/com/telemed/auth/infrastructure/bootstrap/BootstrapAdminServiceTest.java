package com.telemed.auth.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.PasswordEncoder;
import com.telemed.auth.application.port.output.PasswordPolicy;
import com.telemed.auth.application.service.OutboxEventWriter;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.PasswordHash;
import com.telemed.auth.domain.valueobject.UserRole;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BootstrapAdminServiceTest {

  private static final String EMAIL = "admin@example.com";
  private static final String PASSWORD = "Root123456!";
  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  private final AuthUserRepository repository = mock(AuthUserRepository.class);
  private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
  private final PasswordPolicy passwordPolicy = mock(PasswordPolicy.class);
  private final Clock clock = mock(Clock.class);
  private final OutboxEventWriter outboxEventWriter = mock(OutboxEventWriter.class);

  private BootstrapAdminService service;

  @BeforeEach
  void setUp() {
    service =
        new BootstrapAdminService(
            repository, passwordEncoder, passwordPolicy, clock, outboxEventWriter);
  }

  @Test
  void createsActiveAdmin() {
    when(repository.existsByEmail(new Email(EMAIL))).thenReturn(false);
    when(passwordPolicy.validate(PASSWORD, EMAIL)).thenReturn(List.of());
    when(passwordEncoder.hash(PASSWORD)).thenReturn(new PasswordHash("hash"));
    when(clock.now()).thenReturn(NOW);
    when(repository.save(any(AuthUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(service.createIfMissing(EMAIL, PASSWORD)).isTrue();

    ArgumentCaptor<AuthUser> captor = ArgumentCaptor.forClass(AuthUser.class);
    verify(repository).save(captor.capture());
    AuthUser saved = captor.getValue();
    assertThat(saved.status().name()).isEqualTo("ACTIVE");
    assertThat(saved.emailVerified()).isTrue();
    assertThat(saved.roles()).contains(UserRole.ADMIN);
    assertThat(saved.findLocalIdentity()).isPresent();
    verify(outboxEventWriter).write(saved, null);
  }

  @Test
  void skipsExistingUser() {
    when(repository.existsByEmail(new Email(EMAIL))).thenReturn(true);

    assertThat(service.createIfMissing(EMAIL, PASSWORD)).isFalse();

    verify(repository, never()).save(any());
  }

  @Test
  void rejectsWeakBootstrapPassword() {
    when(repository.existsByEmail(new Email(EMAIL))).thenReturn(false);
    when(passwordPolicy.validate(PASSWORD, EMAIL)).thenReturn(List.of("too weak"));

    assertThatThrownBy(() -> service.createIfMissing(EMAIL, PASSWORD))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("password");
  }
}
