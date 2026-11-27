package com.telemed.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.auth.application.dto.command.OAuthLinkConfirmationCommand;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.OAuthLinkConfirmationTokenRepository;
import com.telemed.auth.domain.exception.InvalidOAuthConfirmationTokenException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.AuthStatus;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.PasswordHash;
import com.telemed.auth.domain.valueobject.ProviderType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OAuthLinkConfirmationServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  private final OAuthLinkConfirmationTokenRepository tokenRepository =
      mock(OAuthLinkConfirmationTokenRepository.class);
  private final AuthUserRepository authUserRepository = mock(AuthUserRepository.class);
  private final OutboxEventWriter outboxEventWriter = mock(OutboxEventWriter.class);
  private final AuthAuditLogger audit = mock(AuthAuditLogger.class);
  private final Clock clock = mock(Clock.class);

  private OAuthLinkConfirmationService service;
  private AuthUser user;
  private OAuthLinkConfirmationTokenRepository.Stored token;

  @BeforeEach
  void setUp() {
    service =
        new OAuthLinkConfirmationService(
            tokenRepository, authUserRepository, outboxEventWriter, audit, clock);
    user =
        AuthUser.registerWithPassword(new Email("user@example.com"), new PasswordHash("hash"), NOW);
    user.verifyEmail(NOW);
    user.activate(NOW);
    user.pullEvents();
    token =
        new OAuthLinkConfirmationTokenRepository.Stored(
            UUID.randomUUID(),
            user.id(),
            ProviderType.GOOGLE,
            "hash",
            NOW.plusSeconds(600),
            false,
            "google-sub",
            "user@example.com",
            true);
    when(clock.now()).thenReturn(NOW);
    when(tokenRepository.findUsable("raw-token", NOW)).thenReturn(Optional.of(token));
    when(authUserRepository.findById(user.id())).thenReturn(Optional.of(user));
    when(authUserRepository.save(any(AuthUser.class))).thenAnswer(i -> i.getArgument(0));
  }

  @Test
  void linksExternalIdentityAndConsumesToken() {
    service.confirm(new OAuthLinkConfirmationCommand("raw-token", ProviderType.GOOGLE));

    assertThat(user.identities()).anyMatch(identity -> identity.provider() == ProviderType.GOOGLE);
    verify(authUserRepository).save(user);
    verify(outboxEventWriter).write(user, null);
    verify(tokenRepository).markUsed(token.id(), NOW);
    verify(audit)
        .record(
            eq(user.id().value()), eq(user.id().value()), eq("OAUTH_LINKED"), eq("SUCCESS"), any());
  }

  @Test
  void activatesPendingUserAndVerifiesEmail() {
    AuthUser pending =
        AuthUser.registerWithPassword(
            new Email("pending@example.com"), new PasswordHash("hash"), NOW);
    pending.pullEvents();
    var pendingToken =
        new OAuthLinkConfirmationTokenRepository.Stored(
            UUID.randomUUID(),
            pending.id(),
            ProviderType.GOOGLE,
            "pending-hash",
            NOW.plusSeconds(600),
            false,
            "google-pending-sub",
            "pending@example.com",
            true);
    when(tokenRepository.findUsable("pending-token", NOW)).thenReturn(Optional.of(pendingToken));
    when(authUserRepository.findById(pending.id())).thenReturn(Optional.of(pending));

    service.confirm(new OAuthLinkConfirmationCommand("pending-token", ProviderType.GOOGLE));

    assertThat(pending.status()).isEqualTo(AuthStatus.ACTIVE);
    assertThat(pending.emailVerified()).isTrue();
    assertThat(pending.identities())
        .anyMatch(identity -> identity.provider() == ProviderType.GOOGLE);
    verify(tokenRepository).markUsed(pendingToken.id(), NOW);
  }

  @Test
  void rejectsProviderMismatch() {
    assertThatThrownBy(
            () ->
                service.confirm(new OAuthLinkConfirmationCommand("raw-token", ProviderType.LOCAL)))
        .isInstanceOf(InvalidOAuthConfirmationTokenException.class);
  }

  @Test
  void rejectsUnknownToken() {
    when(tokenRepository.findUsable("missing", NOW)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> service.confirm(new OAuthLinkConfirmationCommand("missing", ProviderType.GOOGLE)))
        .isInstanceOf(InvalidOAuthConfirmationTokenException.class);
  }
}
