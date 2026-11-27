package com.telemed.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.auth.application.dto.command.VerifyEmailCommand;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.EmailVerificationTokenRepository;
import com.telemed.auth.domain.exception.InvalidVerificationTokenException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.PasswordHash;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VerifyEmailServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Mock EmailVerificationTokenRepository tokenRepository;
  @Mock AuthUserRepository authUserRepository;
  @Mock OutboxEventWriter outboxEventWriter;
  @Mock Clock clock;

  private VerifyEmailService service;

  @BeforeEach
  void setUp() {
    service = new VerifyEmailService(tokenRepository, authUserRepository, outboxEventWriter, clock);
  }

  @Test
  void verifiesEmailAndActivatesPendingUser() {
    UUID tokenId = UUID.randomUUID();
    AuthUser user =
        AuthUser.registerWithPassword(
            new Email("patient@example.com"), new PasswordHash("hash"), NOW);
    var stored =
        new EmailVerificationTokenRepository.Stored(
            tokenId, user.id(), "hash", NOW.plusSeconds(3600), false);

    when(clock.now()).thenReturn(NOW);
    when(tokenRepository.findUsable("raw-token", NOW)).thenReturn(Optional.of(stored));
    when(authUserRepository.findById(user.id())).thenReturn(Optional.of(user));

    service.verify(new VerifyEmailCommand("raw-token"));

    assertThat(user.emailVerified()).isTrue();
    assertThat(user.status().name()).isEqualTo("ACTIVE");
    verify(tokenRepository).markUsed(tokenId, NOW);
    verify(outboxEventWriter).write(user, null);
  }

  @Test
  void rejectsUnknownToken() {
    when(clock.now()).thenReturn(NOW);
    when(tokenRepository.findUsable("bad", NOW)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.verify(new VerifyEmailCommand("bad")))
        .isInstanceOf(InvalidVerificationTokenException.class);
  }
}
