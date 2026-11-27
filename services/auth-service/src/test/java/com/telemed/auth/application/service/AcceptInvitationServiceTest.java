package com.telemed.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.auth.application.dto.command.AcceptInvitationCommand;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.InvitationRepository;
import com.telemed.auth.application.port.output.InvitationTokenGenerator;
import com.telemed.auth.application.port.output.PasswordEncoder;
import com.telemed.auth.application.port.output.PasswordPolicy;
import com.telemed.auth.domain.model.Actor;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.Invitation;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.PasswordHash;
import com.telemed.auth.domain.valueobject.TokenHash;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AcceptInvitationServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Mock InvitationRepository invitationRepository;
  @Mock InvitationTokenGenerator tokenGenerator;
  @Mock AuthUserRepository authUserRepository;
  @Mock PasswordEncoder passwordEncoder;
  @Mock PasswordPolicy passwordPolicy;
  @Mock OutboxEventWriter outboxEventWriter;
  @Mock Clock clock;

  private AcceptInvitationService service;

  @BeforeEach
  void setUp() {
    service =
        new AcceptInvitationService(
            invitationRepository,
            tokenGenerator,
            authUserRepository,
            passwordEncoder,
            passwordPolicy,
            outboxEventWriter,
            clock);
  }

  @Test
  void acceptsInvitationAndActivatesUser() {
    TokenHash tokenHash = new TokenHash("hashed-token");
    AuthUser user =
        AuthUser.invite(
            new Email("doctor@example.com"),
            Set.of(UserRole.DOCTOR),
            NOW.plus(Duration.ofDays(7)),
            Actor.user(UserId.newId(), Set.of(UserRole.ADMIN)),
            NOW);
    Invitation invitation = Invitation.create(user.id(), tokenHash, NOW, Duration.ofDays(7));

    when(clock.now()).thenReturn(NOW);
    when(tokenGenerator.hash("raw-token")).thenReturn(tokenHash);
    when(invitationRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(invitation));
    when(authUserRepository.findById(user.id())).thenReturn(Optional.of(user));
    when(passwordPolicy.validate(anyString(), anyString())).thenReturn(List.of());
    when(passwordEncoder.hash("StrongPassw0rd!")).thenReturn(new PasswordHash("hash"));
    when(authUserRepository.save(any(AuthUser.class))).thenAnswer(inv -> inv.getArgument(0));

    var result = service.accept(new AcceptInvitationCommand("raw-token", "StrongPassw0rd!"));

    assertThat(result.status()).isEqualTo("ACTIVE");
    assertThat(result.invitationRequired()).isFalse();
    assertThat(invitation.accepted()).isTrue();
    assertThat(user.findLocalIdentity()).isPresent();
    verify(invitationRepository).save(invitation);
    verify(outboxEventWriter).write(user, null);
  }
}
