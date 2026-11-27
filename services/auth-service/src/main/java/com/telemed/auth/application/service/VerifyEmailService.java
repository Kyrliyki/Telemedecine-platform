package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.VerifyEmailCommand;
import com.telemed.auth.application.port.input.VerifyEmailUseCase;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.EmailVerificationTokenRepository;
import com.telemed.auth.domain.exception.InvalidVerificationTokenException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.AuthStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyEmailService implements VerifyEmailUseCase {

  private final EmailVerificationTokenRepository tokenRepository;
  private final AuthUserRepository authUserRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final Clock clock;

  @Override
  @Transactional
  public void verify(VerifyEmailCommand command) {
    var now = clock.now();
    var token =
        tokenRepository
            .findUsable(command.rawToken(), now)
            .orElseThrow(InvalidVerificationTokenException::new);

    AuthUser user =
        authUserRepository
            .findById(token.userId())
            .orElseThrow(InvalidVerificationTokenException::new);

    user.verifyEmail(now);
    if (user.status() == AuthStatus.PENDING_EMAIL_VERIFICATION) {
      user.activate(now);
    }

    tokenRepository.markUsed(token.id(), now);
    authUserRepository.save(user);
    outboxEventWriter.write(user, null);
  }
}
