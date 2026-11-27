package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.AcceptInvitationCommand;
import com.telemed.auth.application.dto.view.UserRegistrationResult;
import com.telemed.auth.application.port.input.AcceptInvitationUseCase;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.InvitationRepository;
import com.telemed.auth.application.port.output.InvitationTokenGenerator;
import com.telemed.auth.application.port.output.PasswordEncoder;
import com.telemed.auth.application.port.output.PasswordPolicy;
import com.telemed.auth.domain.exception.InvalidInvitationException;
import com.telemed.auth.domain.exception.InvalidPasswordException;
import com.telemed.auth.domain.exception.InvitationAlreadyUsedException;
import com.telemed.auth.domain.exception.InvitationExpiredException;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.Invitation;
import com.telemed.auth.domain.valueobject.PasswordHash;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AcceptInvitationService implements AcceptInvitationUseCase {

  private final InvitationRepository invitationRepository;
  private final InvitationTokenGenerator tokenGenerator;
  private final AuthUserRepository authUserRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicy passwordPolicy;
  private final OutboxEventWriter outboxEventWriter;
  private final Clock clock;

  @Override
  public UserRegistrationResult accept(AcceptInvitationCommand command) {
    var now = clock.now();
    Invitation invitation =
        invitationRepository
            .findByTokenHash(tokenGenerator.hash(command.rawInviteToken()))
            .orElseThrow(InvalidInvitationException::new);

    if (invitation.accepted()) {
      throw new InvitationAlreadyUsedException();
    }
    if (!invitation.isUsable(now)) {
      throw new InvitationExpiredException();
    }

    AuthUser user =
        authUserRepository
            .findById(invitation.userId())
            .orElseThrow(InvalidInvitationException::new);

    var violations = passwordPolicy.validate(command.rawPassword(), user.email().value());
    if (!violations.isEmpty()) {
      throw new InvalidPasswordException(violations);
    }

    PasswordHash passwordHash = passwordEncoder.hash(command.rawPassword());
    user.acceptInvitation(passwordHash, now);
    invitation.accept(now);

    invitationRepository.save(invitation);
    AuthUser saved = authUserRepository.save(user);
    outboxEventWriter.write(user, null);

    return new UserRegistrationResult(
        saved.id().value(),
        saved.email().value(),
        saved.roles().stream().map(Enum::name).collect(Collectors.toSet()),
        saved.status().name(),
        false);
  }
}
