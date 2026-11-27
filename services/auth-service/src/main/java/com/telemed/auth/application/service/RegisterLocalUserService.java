package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.RegisterLocalCommand;
import com.telemed.auth.application.dto.view.UserRegistrationResult;
import com.telemed.auth.application.exception.EmailAlreadyRegisteredException;
import com.telemed.auth.application.mapper.DomainEventMapper;
import com.telemed.auth.application.port.input.RegisterLocalUserUseCase;
import com.telemed.auth.application.port.output.*;
import com.telemed.auth.domain.exception.InvalidPasswordException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.PasswordHash;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.common.security.util.CorrelationContext;
import jakarta.transaction.Transactional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class RegisterLocalUserService implements RegisterLocalUserUseCase {
  private final AuthUserRepository authUserRepository;
  private final OutboxRepository outboxRepository;
  private final OutboxEventFactory outboxEventFactory;
  private final DomainEventMapper eventMapper;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final EmailVerificationTokenGenerator verificationTokenGenerator;
  private final EmailVerificationTokenRepository verificationTokenRepository;
  private final Clock clock;

  @Override
  public UserRegistrationResult register(RegisterLocalCommand command) {
    Email email = new Email(command.email());

    if (authUserRepository.existsByEmail(email)) {
      throw new EmailAlreadyRegisteredException(email.value());
    }
    var violations = passwordPolicy.validate(command.rawPassword(), email.value());
    if (!violations.isEmpty()) {
      throw new InvalidPasswordException(violations);
    }

    PasswordHash hash = passwordEncoder.hash(command.rawPassword());
    var now = clock.now();
    AuthUser user = AuthUser.registerWithPassword(email, hash, now);

    user.requestEmailVerification(now);

    AuthUser saved = authUserRepository.save(user);

    var generated = verificationTokenGenerator.generate(user.id(), now);
    verificationTokenRepository.save(generated.stored());

    user.pullEvents()
        .forEach(
            e ->
                outboxRepository.save(
                    outboxEventFactory.toEntity(
                        eventMapper.map(e, CorrelationContext.current(), generated.rawToken()))));

    return new UserRegistrationResult(
        saved.id().value(),
        saved.email().value(),
        saved.roles().stream().map(Enum::name).collect(Collectors.toSet()),
        saved.status().name(),
        false);
  }
}
