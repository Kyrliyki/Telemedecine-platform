package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.LoginWithOAuthCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;
import com.telemed.auth.application.mapper.DomainEventMapper;
import com.telemed.auth.application.port.input.LoginWithOAuthUseCase;
import com.telemed.auth.application.port.output.*;
import com.telemed.auth.domain.exception.OAuthAccountConflictException;
import com.telemed.auth.domain.exception.UserBlockedException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.ExternalIdentity;
import com.telemed.auth.domain.valueobject.AuthStatus;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.common.security.util.CorrelationContext;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = OAuthAccountConflictException.class)
public class RegisterWithOAuthService implements LoginWithOAuthUseCase {
  private final AuthUserRepository authUserRepository;
  private final OutboxRepository outboxRepository;
  private final OutboxEventFactory outboxEventFactory;
  private final DomainEventMapper eventMapper;
  private final OAuthProviderClient oauthClient;
  private final OAuthLinkConfirmationTokenGenerator confirmationTokenGenerator;
  private final OAuthLinkConfirmationTokenRepository confirmationTokenRepository;
  private final TokenIssuer tokenIssuer;
  private final AuthAuditLogger audit;
  private final Clock clock;

  @Override
  public TokenPairResult loginWithGoogle(LoginWithOAuthCommand command) {
    ExternalIdentity google = oauthClient.exchangeCode(command.authorizationCode());
    Instant now = clock.now();

    Optional<AuthUser> bySubject =
        authUserRepository.findByExternalSubject(google.provider(), google.subject());
    if (bySubject.isPresent()) {
      AuthUser user = bySubject.get();
      user.ensureCanAuthenticate();
      save(user);
      TokenPairResult tokens = tokenIssuer.issue(user);
      audit.loginSucceeded(user.id().value(), "GOOGLE");
      return tokens;
    }

    Optional<AuthUser> byEmail = authUserRepository.findByEmail(google.email());
    if (byEmail.isPresent()) {
      AuthUser existing = byEmail.get();
      if (existing.status() == AuthStatus.BLOCKED) {
        throw new UserBlockedException(existing.id(), "account not active");
      }
      if (!existing.emailVerified()) {

        var generated =
            confirmationTokenGenerator.generate(
                existing.id(),
                google.provider(),
                google.subject().value(),
                google.email().value(),
                google.emailVerified(),
                now);
        confirmationTokenRepository.save(generated.stored());
        existing.requestOAuthLinkConfirmation(google.provider(), now);
        saveWithToken(existing, generated.rawToken());
        throw new OAuthAccountConflictException(google.provider().name());
      }

      existing.linkExternalIdentity(google);
      save(existing);
      TokenPairResult tokens = tokenIssuer.issue(existing);
      audit.loginSucceeded(existing.id().value(), "GOOGLE");
      return tokens;
    }

    AuthUser user =
        AuthUser.registerWithExternal(
            google.email(), google.provider(), google.subject(), google.emailVerified(), now);
    save(user);
    TokenPairResult tokens = tokenIssuer.issue(user);
    audit.loginSucceeded(user.id().value(), "GOOGLE");
    return tokens;
  }

  private void save(AuthUser user) {
    authUserRepository.save(user);
    user.pullEvents()
        .forEach(
            e ->
                outboxRepository.save(
                    outboxEventFactory.toEntity(
                        eventMapper.map(e, CorrelationContext.current(), null))));
  }

  private void saveWithToken(AuthUser user, String rawToken) {
    authUserRepository.save(user);
    user.pullEvents()
        .forEach(
            e ->
                outboxRepository.save(
                    outboxEventFactory.toEntity(
                        eventMapper.map(e, CorrelationContext.current(), rawToken))));
  }
}
