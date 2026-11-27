package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.OAuthLinkConfirmationCommand;
import com.telemed.auth.application.port.input.OAuthLinkConfirmationUseCase;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.OAuthLinkConfirmationTokenRepository;
import com.telemed.auth.domain.exception.InvalidOAuthConfirmationTokenException;
import com.telemed.auth.domain.exception.OAuthAlreadyLinkedException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.ExternalIdentity;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.ProviderSubject;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class OAuthLinkConfirmationService implements OAuthLinkConfirmationUseCase {

  private final OAuthLinkConfirmationTokenRepository tokenRepository;
  private final AuthUserRepository authUserRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final AuthAuditLogger audit;
  private final Clock clock;

  @Override
  public void confirm(OAuthLinkConfirmationCommand command) {
    Instant now = clock.now();
    var token =
        tokenRepository
            .findUsable(command.rawToken(), now)
            .orElseThrow(InvalidOAuthConfirmationTokenException::new);

    if (token.provider() != command.provider()
        || token.providerSubject() == null
        || token.providerEmail() == null) {
      throw new InvalidOAuthConfirmationTokenException();
    }

    AuthUser user =
        authUserRepository
            .findById(token.userId())
            .orElseThrow(InvalidOAuthConfirmationTokenException::new);

    ExternalIdentity identity =
        ExternalIdentity.link(
            token.provider(),
            ProviderSubject.fromExternal(token.providerSubject()),
            new Email(token.providerEmail()),
            token.providerEmailVerified(),
            now);

    try {
      user.confirmOAuthLink(identity, now);
    } catch (IllegalStateException exception) {
      throw new OAuthAlreadyLinkedException(token.provider().name());
    }

    authUserRepository.save(user);
    outboxEventWriter.write(user, null);
    tokenRepository.markUsed(token.id(), now);
    audit.record(
        user.id().value(),
        user.id().value(),
        "OAUTH_LINKED",
        "SUCCESS",
        Map.of("provider", token.provider().name()));
  }
}
