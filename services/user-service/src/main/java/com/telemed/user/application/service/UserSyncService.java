package com.telemed.user.application.service;

import com.telemed.common.events.AuthEventPayloads.UserBlockedPayload;
import com.telemed.common.events.AuthEventPayloads.UserCreatedByAdminPayload;
import com.telemed.common.events.AuthEventPayloads.UserEmailChangedPayload;
import com.telemed.common.events.AuthEventPayloads.UserInvitedPayload;
import com.telemed.common.events.AuthEventPayloads.UserRegisteredPayload;
import com.telemed.common.events.AuthEventPayloads.UserRoleChangedPayload;
import com.telemed.common.events.AuthEventPayloads.UserUnblockedPayload;
import com.telemed.user.application.exception.EmailAlreadyInUseException;
import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.DoctorProfileRepository;
import com.telemed.user.application.port.output.UserRepository;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import com.telemed.user.infrastructure.audit.UserAuditLogger;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserSyncService {

  private static final Logger log = LoggerFactory.getLogger(UserSyncService.class);

  private final UserRepository userRepository;
  private final DoctorProfileRepository doctorProfileRepository;
  private final Clock clock;
  private final UserAuditLogger audit;

  public UserSyncService(
      UserRepository userRepository,
      DoctorProfileRepository doctorProfileRepository,
      Clock clock,
      UserAuditLogger audit) {
    this.userRepository = userRepository;
    this.doctorProfileRepository = doctorProfileRepository;
    this.clock = clock;
    this.audit = audit;
  }

  @Transactional
  public void onUserRegistered(UserRegisteredPayload payload) {
    upsert(payload.userId(), payload.email(), parseRoles(payload.roles()));
  }

  @Transactional
  public void onUserCreatedByAdmin(UserCreatedByAdminPayload payload) {
    upsert(payload.userId(), payload.email(), parseRoles(payload.roles()));
  }

  @Transactional
  public void onUserInvited(UserInvitedPayload payload) {
    upsert(payload.userId(), payload.email(), parseRoles(payload.roles()));
  }

  @Transactional
  public void onRoleChanged(UserRoleChangedPayload payload) {
    userRepository
        .findById(new UserId(payload.userId()))
        .ifPresentOrElse(
            user -> {
              Instant now = clock.now();
              user.replaceRoles(parseRoles(payload.newRoles()), now);
              user.markAuthUpdatedAt(now);
              userRepository.save(user);
              audit.identityRolesChanged(payload.userId());
              if (!user.isDoctor()) {
                doctorProfileRepository.deleteByUserId(user.getId());
              }
            },
            () -> log.warn("Role changed for unknown profile userId={}", payload.userId()));
  }

  @Transactional
  public void onEmailChanged(UserEmailChangedPayload payload) {
    userRepository
        .findById(new UserId(payload.userId()))
        .ifPresentOrElse(
            user -> {
              Email newEmail = new Email(payload.newEmail());
              userRepository
                  .findByEmail(newEmail)
                  .filter(other -> !other.getId().equals(user.getId()))
                  .ifPresent(
                      other -> {
                        throw new EmailAlreadyInUseException(newEmail.value());
                      });
              user.changeEmail(newEmail, clock.now());
              user.markAuthUpdatedAt(clock.now());
              userRepository.save(user);
              audit.identityEmailChanged(payload.userId());
            },
            () -> log.warn("Email changed for unknown profile userId={}", payload.userId()));
  }

  @Transactional
  public void onBlocked(UserBlockedPayload payload) {
    userRepository
        .findById(new UserId(payload.userId()))
        .ifPresentOrElse(
            user -> {
              Instant now = clock.now();
              user.block(now);
              user.markAuthUpdatedAt(now);
              userRepository.save(user);
              audit.identityStatusChanged(payload.userId(), "BLOCKED");
            },
            () -> log.warn("Blocked event for unknown profile userId={}", payload.userId()));
  }

  @Transactional
  public void onUnblocked(UserUnblockedPayload payload) {
    userRepository
        .findById(new UserId(payload.userId()))
        .ifPresentOrElse(
            user -> {
              Instant now = clock.now();
              user.unblock(now);
              user.markAuthUpdatedAt(now);
              userRepository.save(user);
              audit.identityStatusChanged(payload.userId(), "ACTIVE");
            },
            () -> log.warn("Unblocked event for unknown profile userId={}", payload.userId()));
  }

  @Transactional
  public void applySnapshot(
      UUID userId, String email, Set<String> roles, String authStatus, Instant authUpdatedAt) {
    UserId id = new UserId(userId);
    String status = authStatus == null ? "" : authStatus.toUpperCase(Locale.ROOT);
    Set<UserRole> parsedRoles = parseRoles(roles == null ? List.of() : List.copyOf(roles));
    Instant now = clock.now();
    Instant sourceUpdatedAt = authUpdatedAt == null ? now : authUpdatedAt;
    User existing = userRepository.findById(id).orElse(null);
    if (existing == null) {
      Email parsedEmail = new Email(email);
      User created = User.fromAuth(id, parsedEmail, parsedRoles, now);
      if ("BLOCKED".equals(status)) {
        created.block(now);
      }
      created.markAuthUpdatedAt(sourceUpdatedAt);
      userRepository.save(created);
      audit.identityCreated(userId);
      log.info("Reconciled missing profile for auth user userId={}", userId);
      return;
    }

    if (existing.getAuthUpdatedAt() != null
        && sourceUpdatedAt.isBefore(existing.getAuthUpdatedAt())) {
      log.debug("Skipping stale auth snapshot for userId={}", userId);
      return;
    }

    Email parsedEmail = new Email(email);
    UserStatus desiredStatus = "BLOCKED".equals(status) ? UserStatus.BLOCKED : UserStatus.ACTIVE;
    if (existing.getEmail().equals(parsedEmail)
        && existing.getRoles().equals(parsedRoles)
        && existing.getStatus() == desiredStatus
        && sourceUpdatedAt.equals(existing.getAuthUpdatedAt())) {
      return;
    }

    existing.changeEmail(parsedEmail, now);
    existing.replaceRoles(parsedRoles, now);
    if (desiredStatus == UserStatus.BLOCKED) {
      existing.block(now);
    } else {
      existing.unblock(now);
    }
    existing.markAuthUpdatedAt(sourceUpdatedAt);
    userRepository.save(existing);
    if (!existing.isDoctor()) {
      doctorProfileRepository.deleteByUserId(existing.getId());
    }
  }

  private void upsert(UUID userId, String email, Set<UserRole> roles) {
    Instant now = clock.now();
    UserId id = new UserId(userId);
    Email parsedEmail = new Email(email);

    User existing = userRepository.findById(id).orElse(null);
    if (existing == null) {
      userRepository
          .findByEmail(parsedEmail)
          .filter(other -> !other.getId().equals(id))
          .ifPresent(
              other -> {
                throw new EmailAlreadyInUseException(parsedEmail.value());
              });
      User created = User.fromAuth(id, parsedEmail, roles, now);
      userRepository.save(created);
      audit.identityCreated(userId);
      log.info("Created profile for auth user userId={}", userId);
      return;
    }

    existing.changeEmail(parsedEmail, now);
    existing.replaceRoles(roles, now);
    existing.markAuthUpdatedAt(now);
    userRepository.save(existing);
    audit.identityRolesChanged(userId);
  }

  private static Set<UserRole> parseRoles(List<String> roles) {
    EnumSet<UserRole> parsed = EnumSet.noneOf(UserRole.class);
    if (roles != null) {
      for (String role : roles) {
        try {
          parsed.add(UserRole.valueOf(role.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {

        }
      }
    }
    if (parsed.isEmpty()) {
      parsed.add(UserRole.DEFAULT);
    }
    return parsed;
  }
}
