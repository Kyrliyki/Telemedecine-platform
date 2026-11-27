package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.LoginLocalCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;
import com.telemed.auth.application.exception.InvalidCredentialsException;
import com.telemed.auth.application.port.input.LoginLocalUserUseCase;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.PasswordEncoder;
import com.telemed.auth.application.port.output.TokenIssuer;
import com.telemed.auth.domain.exception.UserBlockedException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.LocalIdentity;
import com.telemed.auth.domain.valueobject.Email;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LoginService implements LoginLocalUserUseCase {

  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final TokenIssuer tokenIssuer;
  private final AuthAuditLogger audit;

  @Override
  public TokenPairResult login(LoginLocalCommand command) {
    UUID targetUserId = null;
    try {
      Email email = new Email(command.email());
      AuthUser user =
          authUserRepository.findByEmail(email).orElseThrow(InvalidCredentialsException::new);
      targetUserId = user.id().value();

      user.ensureCanAuthenticate();

      LocalIdentity local = user.findLocalIdentity().orElseThrow(InvalidCredentialsException::new);

      if (!passwordEncoder.matches(command.rawPassword(), local.credential().passwordHash())) {
        throw new InvalidCredentialsException();
      }

      TokenPairResult tokens = tokenIssuer.issue(user);
      audit.loginSucceeded(targetUserId, "PASSWORD");
      return tokens;
    } catch (UserBlockedException exception) {
      audit.loginFailed(targetUserId, command.email(), "BLOCKED");
      throw exception;
    } catch (InvalidCredentialsException exception) {
      audit.loginFailed(targetUserId, command.email(), "INVALID_CREDENTIALS");
      throw exception;
    } catch (RuntimeException exception) {
      audit.loginFailed(targetUserId, command.email(), "INVALID_REQUEST");
      throw exception;
    }
  }
}
