package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.CreateUserByAdminCommand;
import com.telemed.auth.application.dto.view.UserRegistrationResult;
import com.telemed.auth.application.mapper.DomainEventMapper;
import com.telemed.auth.application.policy.InvitationPolicy;
import com.telemed.auth.application.port.input.CreateUserByAdminUseCase;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.InvitationRepository;
import com.telemed.auth.application.port.output.InvitationTokenGenerator;
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
public class CreateUserByAdminService implements CreateUserByAdminUseCase {

  private final AuthUserRepository authUserRepository;
  private final OutboxRepository outboxRepository;
  private final InvitationTokenGenerator tokenGenerator;
  private final InvitationRepository invitationRepository;
  private final OutboxEventFactory outboxEventFactory;
  private final InvitationPolicy invitationPolicy;
  private final DomainEventMapper eventMapper;
  private final Clock clock;

  @Override
  @Transactional
  public UserRegistrationResult createByAdmin(CreateUserByAdminCommand command) {
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
    AuthUser user = AuthUser.createByAdmin(email, roles, actor, now);

    var generated = tokenGenerator.generate();
    Invitation invitation =
        Invitation.create(user.id(), generated.tokenHash(), now, invitationPolicy.ttl());

    AuthUser saved = authUserRepository.save(user);
    invitationRepository.save(invitation);

    user.pullEvents()
        .forEach(
            e -> {
              var envelope = eventMapper.map(e, CorrelationContext.current(), generated.rawToken());
              outboxRepository.save(outboxEventFactory.toEntity(envelope));
            });
    return new UserRegistrationResult(
        saved.id().value(),
        saved.email().value(),
        saved.roles().stream().map(Enum::name).collect(Collectors.toSet()),
        saved.status().name(),
        true);
  }
}
