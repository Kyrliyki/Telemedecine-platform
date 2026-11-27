package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.InviteUserCommand;
import com.telemed.auth.application.dto.view.InvitationResult;
import com.telemed.auth.application.mapper.DomainEventMapper;
import com.telemed.auth.application.policy.InvitationPolicy;
import com.telemed.auth.application.port.input.InviteUserUseCase;
import com.telemed.auth.application.port.output.*;
import com.telemed.auth.domain.exception.EmailAlreadyExistsException;
import com.telemed.auth.domain.model.Actor;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.model.Invitation;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.common.security.util.CorrelationContext;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class InviteUserService implements InviteUserUseCase {

  private final AuthUserRepository authUserRepository;
  private final InvitationRepository invitationRepository;
  private final OutboxRepository outboxRepository;
  private final OutboxEventFactory outboxEventFactory;
  private final DomainEventMapper eventMapper;
  private final InvitationTokenGenerator tokenGenerator;
  private final InvitationPolicy invitationPolicy;
  private final Clock clock;

  @Override
  public InvitationResult invite(InviteUserCommand command) {
    Email email = new Email(command.email());
    if (authUserRepository.existsByEmail(email)) {
      throw new EmailAlreadyExistsException(email);
    }

    Set<UserRole> roles =
        command.roles().stream()
            .map(role -> UserRole.valueOf(role.toUpperCase(java.util.Locale.ROOT)))
            .collect(Collectors.toSet());
    Actor actor = Actor.user(new UserId(command.actorUserId()), Set.of(UserRole.ADMIN));

    var now = clock.now();
    var ttl = invitationPolicy.ttl();

    AuthUser user = AuthUser.invite(email, roles, now.plus(ttl), actor, now);
    var generated = tokenGenerator.generate();
    Invitation invitation = Invitation.create(user.id(), generated.tokenHash(), now, ttl);

    AuthUser saved = authUserRepository.save(user);
    invitationRepository.save(invitation);

    user.pullEvents()
        .forEach(
            e ->
                outboxRepository.save(
                    outboxEventFactory.toEntity(
                        eventMapper.map(e, CorrelationContext.current(), generated.rawToken()))));

    return new InvitationResult(saved.id().value(), saved.email().value(), invitation.expiresAt());
  }
}
