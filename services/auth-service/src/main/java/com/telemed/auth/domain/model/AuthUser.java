package com.telemed.auth.domain.model;

import com.telemed.auth.domain.event.*;
import com.telemed.auth.domain.exception.InvalidStatusTransitionException;
import com.telemed.auth.domain.exception.UserBlockedException;
import com.telemed.auth.domain.valueobject.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class AuthUser {
  private final UserId id;
  private Email email;
  private boolean emailVerified;
  private AuthStatus status;
  private final Set<UserRole> roles;
  private final Set<UserIdentity> identities;
  private final Instant createdAt;
  private Instant updatedAt;
  private final List<DomainEvent> events = new ArrayList<>();

  private AuthUser(
      UserId id,
      Email email,
      boolean emailVerified,
      AuthStatus status,
      Set<UserRole> roles,
      Set<UserIdentity> identities,
      Instant createdAt,
      Instant updatedAt) {
    this.id = id;
    this.email = email;
    this.emailVerified = emailVerified;
    this.status = Objects.requireNonNull(status);
    this.roles = new HashSet<>(Objects.requireNonNull(roles));
    this.identities = new HashSet<>(Objects.requireNonNull(identities));
    this.createdAt = Objects.requireNonNull(createdAt);
    this.updatedAt = Objects.requireNonNull(updatedAt);

    if (this.roles.isEmpty()) {
      throw new IllegalArgumentException("User must have at least one role");
    }

    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt cannot be before createdAt");
    }
  }

  public static AuthUser rehydrate(
      UserId id,
      Email email,
      boolean emailVerified,
      AuthStatus status,
      Set<UserRole> roles,
      Set<UserIdentity> identities,
      Instant createdAt,
      Instant updatedAt) {
    return new AuthUser(id, email, emailVerified, status, roles, identities, createdAt, updatedAt);
  }

  public static AuthUser createByAdmin(Email email, Set<UserRole> roles, Actor actor, Instant now) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("At least one role required");
    }
    AuthUser user =
        new AuthUser(
            UserId.newId(), email, false, AuthStatus.PENDING_INVITATION, roles, Set.of(), now, now);
    user.events.add(
        new UserCreatedByAdminEvent(
            UUID.randomUUID(), now, user.id, email, Set.copyOf(roles), actor.userId()));
    return user;
  }

  private Optional<LocalIdentity> findLocal() {
    return identities.stream()
        .filter(LocalIdentity.class::isInstance)
        .map(LocalIdentity.class::cast)
        .findFirst();
  }

  private Optional<ExternalIdentity> findExternal(ProviderType provider) {
    return identities.stream()
        .filter(ExternalIdentity.class::isInstance)
        .map(ExternalIdentity.class::cast)
        .filter(e -> e.provider() == provider)
        .findFirst();
  }

  private void replaceLocal(LocalIdentity updated) {
    identities.removeIf(LocalIdentity.class::isInstance);
    identities.add(updated);
  }

  private Instant requireValidUpdateTime(Instant now) {
    Objects.requireNonNull(now);

    if (now.isBefore(updatedAt)) {
      throw new IllegalArgumentException("Update time cannot be before updatedAt");
    }

    return now;
  }

  public void ensureCanAuthenticate() {
    if (status == AuthStatus.BLOCKED) {
      throw new UserBlockedException(id, "account is blocked");
    }
    if (status != AuthStatus.ACTIVE) {
      throw new InvalidStatusTransitionException(status, AuthStatus.ACTIVE);
    }
  }

  public void changeEmail(Email newEmail, Instant now) {
    if (email.equals(newEmail)) {
      return;
    }
    updatedAt = requireValidUpdateTime(now);
    Email oldEmail = email;
    email = newEmail;
    emailVerified = false;

    events.add(new UserEmailChangedEvent(UUID.randomUUID(), updatedAt, id, oldEmail, newEmail));
  }

  public void verifyEmail(Instant now) {
    if (status == AuthStatus.BLOCKED) {
      throw new InvalidStatusTransitionException(status, AuthStatus.ACTIVE);
    }
    if (emailVerified) {
      return;
    }
    updatedAt = requireValidUpdateTime(now);
    emailVerified = true;
    events.add(new UserEmailVerifiedEvent(UUID.randomUUID(), updatedAt, id, email));
  }

  public void assignRole(UserRole role, Actor actor, Instant now) {
    Objects.requireNonNull(role);
    if (roles.contains(role)) return;

    Set<UserRole> old = Set.copyOf(roles);
    roles.add(role);
    updatedAt = requireValidUpdateTime(now);

    events.add(
        new UserRoleChangedEvent(
            UUID.randomUUID(), updatedAt, id, Set.copyOf(roles), old, actor.userId()));
  }

  public void removeRole(UserRole role, Actor actor, Instant now) {
    Objects.requireNonNull(role);

    if (!roles.contains(role)) return;

    if (roles.size() == 1 && roles.contains(role)) {
      throw new IllegalStateException("User must have at least one role");
    }

    Set<UserRole> old = Set.copyOf(roles);
    roles.remove(role);
    updatedAt = requireValidUpdateTime(now);

    events.add(
        new UserRoleChangedEvent(
            UUID.randomUUID(), updatedAt, id, Set.copyOf(roles), old, actor.userId()));
  }

  public void activate(Instant now) {
    if (status != AuthStatus.PENDING_EMAIL_VERIFICATION) {
      throw new IllegalStateException("Only pending accounts can be activated");
    }

    if (!emailVerified) {
      throw new IllegalStateException("Email must be verified before activation");
    }

    status = AuthStatus.ACTIVE;
    updatedAt = requireValidUpdateTime(now);
  }

  public void block(String reason, Actor actor, Instant now) {
    if (status == AuthStatus.BLOCKED) return;
    status = AuthStatus.BLOCKED;
    updatedAt = requireValidUpdateTime(now);
    events.add(new UserBlockedEvent(UUID.randomUUID(), updatedAt, id, reason, actor.userId()));
  }

  public void unblock(Actor actor, Instant now) {
    if (status != AuthStatus.BLOCKED) {
      throw new InvalidStatusTransitionException(status, AuthStatus.ACTIVE);
    }
    status = emailVerified ? AuthStatus.ACTIVE : AuthStatus.PENDING_EMAIL_VERIFICATION;
    updatedAt = requireValidUpdateTime(now);
    events.add(new UserUnblockedEvent(UUID.randomUUID(), updatedAt, id, actor.userId()));
  }

  public List<DomainEvent> pullEvents() {
    List<DomainEvent> result = List.copyOf(events);
    events.clear();
    return result;
  }

  public UserId id() {
    return id;
  }

  public Email email() {
    return email;
  }

  public boolean emailVerified() {
    return emailVerified;
  }

  public AuthStatus status() {
    return status;
  }

  public Set<UserRole> roles() {
    return Collections.unmodifiableSet(roles);
  }

  public Set<UserIdentity> identities() {
    return Collections.unmodifiableSet(identities);
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public Optional<LocalIdentity> findLocalIdentity() {
    return findLocal();
  }

  public LocalIdentity requireLocalIdentity() {
    return findLocalIdentity()
        .orElseThrow(() -> new IllegalStateException("User " + id + " has no local identity"));
  }

  public static AuthUser registerWithPassword(Email email, PasswordHash passwordHash, Instant now) {

    Objects.requireNonNull(now);
    AuthUser user =
        new AuthUser(
            UserId.newId(),
            email,
            false,
            AuthStatus.PENDING_EMAIL_VERIFICATION,
            Set.of(UserRole.PATIENT),
            Set.of(),
            now,
            now);
    user.linkLocalIdentity(LocalIdentity.create(email, passwordHash, now));
    user.events.add(
        new UserRegisteredEvent(
            UUID.randomUUID(),
            now,
            user.id,
            email,
            Set.copyOf(user.roles),
            false,
            UserRegisteredEvent.RegistrationSource.PASSWORD));
    return user;
  }

  private void linkLocalIdentity(LocalIdentity local) {
    if (findLocal().isPresent()) {
      throw new IllegalStateException("Local identity already linked");
    }
    identities.add(local);
    updatedAt = local.linkedAt();
  }

  public void linkExternalIdentity(ExternalIdentity external) {
    if (findExternal(external.provider()).isPresent()) {
      throw new IllegalStateException("Provider already linked: " + external.provider());
    }
    identities.add(external);
    updatedAt = external.linkedAt();
  }

  public void confirmOAuthLink(ExternalIdentity external, Instant now) {
    Objects.requireNonNull(external, "external identity required");

    if (status == AuthStatus.BLOCKED) {
      throw new UserBlockedException(id, "account is blocked");
    }

    linkExternalIdentity(external);

    if (!emailVerified) {
      emailVerified = true;
      events.add(new UserEmailVerifiedEvent(UUID.randomUUID(), now, id, email));
    }
    if (status == AuthStatus.PENDING_EMAIL_VERIFICATION) {
      status = AuthStatus.ACTIVE;
    }

    updatedAt = requireValidUpdateTime(now);
  }

  public static AuthUser registerWithExternal(
      Email email,
      ProviderType provider,
      ProviderSubject subject,
      boolean emailVerifiedByProvider,
      Instant now) {
    AuthUser user =
        new AuthUser(
            UserId.newId(),
            email,
            emailVerifiedByProvider,
            AuthStatus.ACTIVE,
            Set.of(UserRole.PATIENT),
            Set.of(),
            now,
            now);
    user.linkExternalIdentity(
        ExternalIdentity.link(provider, subject, email, emailVerifiedByProvider, now));
    user.events.add(
        new UserRegisteredEvent(
            UUID.randomUUID(),
            now,
            user.id,
            email,
            Set.copyOf(user.roles),
            emailVerifiedByProvider,
            UserRegisteredEvent.RegistrationSource.GOOGLE));
    return user;
  }

  public static AuthUser invite(
      Email email, Set<UserRole> roles, Instant invitationExpiresAt, Actor actor, Instant now) {
    if (roles == null || roles.isEmpty()) {
      throw new IllegalArgumentException("At least one role required");
    }
    AuthUser user =
        new AuthUser(
            UserId.newId(), email, false, AuthStatus.PENDING_INVITATION, roles, Set.of(), now, now);
    user.events.add(
        new UserInvitedEvent(
            UUID.randomUUID(),
            now,
            user.id,
            email,
            Set.copyOf(roles),
            invitationExpiresAt,
            actor.userId()));
    return user;
  }

  public void acceptInvitation(PasswordHash passwordHash, Instant now) {
    if (status != AuthStatus.PENDING_INVITATION) {
      throw new InvalidStatusTransitionException(status, AuthStatus.ACTIVE);
    }
    if (findLocal().isPresent()) {
      throw new IllegalStateException("Local identity already linked");
    }
    linkLocalIdentity(LocalIdentity.create(email, passwordHash, now));
    emailVerified = true;
    status = AuthStatus.ACTIVE;
    updatedAt = requireValidUpdateTime(now);
    events.add(
        new UserRegisteredEvent(
            UUID.randomUUID(),
            updatedAt,
            id,
            email,
            Set.copyOf(roles),
            true,
            UserRegisteredEvent.RegistrationSource.PASSWORD));
  }

  public void linkIdentity(UserIdentity identity, Instant now) {
    Objects.requireNonNull(identity);

    if (identities.stream().anyMatch(existing -> existing.id().equals(identity.id()))) {
      throw new IllegalArgumentException("Identity with this ID already exists");
    }

    boolean duplicateProviderIdentity =
        identities.stream().anyMatch(existing -> isSameProviderIdentity(existing, identity));

    if (duplicateProviderIdentity) {
      throw new IllegalArgumentException("This provider identity is already linked");
    }

    identities.add(identity);
    updatedAt = requireValidUpdateTime(now);
  }

  private boolean isSameProviderIdentity(UserIdentity first, UserIdentity second) {
    if (first.provider() != second.provider()) {
      return false;
    }

    if (first instanceof LocalIdentity && second instanceof LocalIdentity) {
      return true;
    }

    if (first instanceof ExternalIdentity a && second instanceof ExternalIdentity b) {
      return a.subject().equals(b.subject());
    }

    return false;
  }

  public void unlinkIdentity(UserIdentityId identityId, Instant now) {
    Objects.requireNonNull(identityId);

    UserIdentity identity =
        identities.stream()
            .filter(item -> item.id().equals(identityId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Identity not found"));

    if (identities.size() == 1) {
      throw new IllegalStateException("Cannot unlink the last authentication identity");
    }

    identities.remove(identity);
    updatedAt = requireValidUpdateTime(now);
  }

  public void requestEmailVerification(Instant now) {
    if (emailVerified) return;
    if (status == AuthStatus.BLOCKED) {
      throw new InvalidStatusTransitionException(status, status);
    }
    updatedAt = requireValidUpdateTime(now);
    events.add(
        new UserEmailVerificationRequestedEvent(
            UUID.randomUUID(), now, id, email, now.plus(Duration.ofHours(24))));
  }

  public void requestOAuthLinkConfirmation(ProviderType provider, Instant now) {
    updatedAt = requireValidUpdateTime(now);
    events.add(
        new OAuthLinkConfirmationRequestedEvent(
            UUID.randomUUID(), now, id, email, provider, now.plus(Duration.ofHours(1))));
  }
}
