package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.BlockUserCommand;
import com.telemed.auth.application.port.input.BlockUserUseCase;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.Clock;
import com.telemed.auth.application.port.output.TokenRevocationPort;
import com.telemed.auth.domain.model.Actor;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BlockUserService implements BlockUserUseCase {

  private final AuthUserRepository authUserRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final TokenRevocationPort tokenRevocation;
  private final AuthAuditLogger audit;
  private final Clock clock;

  @Override
  public void block(BlockUserCommand command) {
    AuthUser user = load(command);
    user.block(command.reason(), actor(command), clock.now());
    authUserRepository.save(user);
    outboxEventWriter.write(user, null);
    tokenRevocation.revokeAllForUser(command.targetUserId(), "USER_BLOCKED");
    audit.blocked(command.actorUserId(), command.targetUserId(), command.reason());
  }

  @Override
  public void unblock(BlockUserCommand command) {
    AuthUser user = load(command);
    user.unblock(actor(command), clock.now());
    authUserRepository.save(user);
    outboxEventWriter.write(user, null);
    audit.unblocked(command.actorUserId(), command.targetUserId());
  }

  private AuthUser load(BlockUserCommand command) {
    return authUserRepository
        .findById(new UserId(command.targetUserId()))
        .orElseThrow(
            () -> new IllegalArgumentException("User not found: " + command.targetUserId()));
  }

  private Actor actor(BlockUserCommand command) {
    return Actor.user(new UserId(command.actorUserId()), Set.of(UserRole.ADMIN));
  }
}
