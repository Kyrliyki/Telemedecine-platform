package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.command.AssignRoleCommand;
import com.telemed.auth.application.port.input.AssignRoleUseCase;
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
public class AssignRoleService implements AssignRoleUseCase {

  private final AuthUserRepository authUserRepository;
  private final OutboxEventWriter outboxEventWriter;
  private final TokenRevocationPort tokenRevocation;
  private final AuthAuditLogger audit;
  private final Clock clock;

  @Override
  public void assignRole(AssignRoleCommand command) {
    updateRole(command, true);
  }

  @Override
  public void revokeRole(AssignRoleCommand command) {
    updateRole(command, false);
  }

  private void updateRole(AssignRoleCommand command, boolean assign) {
    AuthUser user =
        authUserRepository
            .findById(new UserId(command.targetUserId()))
            .orElseThrow(
                () -> new IllegalArgumentException("User not found: " + command.targetUserId()));

    UserRole role = UserRole.valueOf(command.role().toUpperCase(java.util.Locale.ROOT));
    Actor actor = Actor.user(new UserId(command.actorUserId()), Set.of(UserRole.ADMIN));
    Set<UserRole> before = Set.copyOf(user.roles());

    if (assign) {
      user.assignRole(role, actor, clock.now());
    } else {
      user.removeRole(role, actor, clock.now());
    }

    if (before.equals(user.roles())) {
      return;
    }

    authUserRepository.save(user);
    outboxEventWriter.write(user, null);

    tokenRevocation.revokeAllForUser(command.targetUserId(), "ROLE_CHANGED");
    if (assign) {
      audit.roleAssigned(command.actorUserId(), command.targetUserId(), role.name());
    } else {
      audit.roleRevoked(command.actorUserId(), command.targetUserId(), role.name());
    }
  }
}
