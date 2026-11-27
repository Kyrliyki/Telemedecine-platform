package com.telemed.user.domain.model;

import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public final class User {

  private final UserId id;
  private final Instant createdAt;

  private Email email;
  private String firstName;
  private String lastName;
  private Set<UserRole> roles;
  private UserStatus status;
  private Instant updatedAt;
  private Instant lastSeenAt;
  private Instant authUpdatedAt;
  private final long version;

  private User(
      UserId id,
      Email email,
      String firstName,
      String lastName,
      Set<UserRole> roles,
      UserStatus status,
      Instant createdAt,
      Instant updatedAt,
      Instant lastSeenAt,
      Instant authUpdatedAt,
      long version) {
    this.id = Objects.requireNonNull(id, "id required");
    this.email = Objects.requireNonNull(email, "email required");
    this.firstName = normalizeName(firstName);
    this.lastName = normalizeName(lastName);
    this.roles = normalizeRoles(roles);
    this.status = Objects.requireNonNull(status, "status required");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt required");
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt cannot be before createdAt");
    }
    this.lastSeenAt = lastSeenAt;
    this.authUpdatedAt = authUpdatedAt;
    this.version = version;
  }

  public static User fromAuth(UserId id, Email email, Set<UserRole> roles, Instant now) {
    return new User(id, email, "", "", roles, UserStatus.ACTIVE, now, now, null, now, 0L);
  }

  public static User rehydrate(
      UserId id,
      Email email,
      String firstName,
      String lastName,
      Set<UserRole> roles,
      UserStatus status,
      Instant createdAt,
      Instant updatedAt,
      Instant lastSeenAt,
      Instant authUpdatedAt,
      long version) {
    return new User(
        id,
        email,
        firstName,
        lastName,
        roles,
        status,
        createdAt,
        updatedAt,
        lastSeenAt,
        authUpdatedAt,
        version);
  }

  public void updateProfile(String firstName, String lastName, Instant now) {
    requireNotBefore(now);
    if (firstName != null) {
      this.firstName = normalizeName(firstName);
    }
    if (lastName != null) {
      this.lastName = normalizeName(lastName);
    }
    this.updatedAt = now;
  }

  public void changeEmail(Email newEmail, Instant now) {
    requireNotBefore(now);
    Objects.requireNonNull(newEmail, "email required");
    if (!email.equals(newEmail)) {
      this.email = newEmail;
      this.updatedAt = now;
    }
  }

  public void replaceRoles(Set<UserRole> newRoles, Instant now) {
    requireNotBefore(now);
    Set<UserRole> normalized = normalizeRoles(newRoles);
    if (!roles.equals(normalized)) {
      this.roles = normalized;
      this.updatedAt = now;
    }
  }

  public void block(Instant now) {
    requireNotBefore(now);
    if (status != UserStatus.BLOCKED) {
      status = UserStatus.BLOCKED;
      updatedAt = now;
    }
  }

  public void unblock(Instant now) {
    requireNotBefore(now);
    if (status != UserStatus.ACTIVE) {
      status = UserStatus.ACTIVE;
      updatedAt = now;
    }
  }

  public void markSeen(Instant now) {
    requireNotBefore(now);
    lastSeenAt = now;
    updatedAt = now;
  }

  public boolean hasRole(UserRole role) {
    return roles.contains(role);
  }

  public boolean isAdmin() {
    return hasRole(UserRole.ADMIN);
  }

  public boolean isDoctor() {
    return hasRole(UserRole.DOCTOR);
  }

  public String displayName() {
    String name = (firstName + " " + lastName).trim();
    return name.isEmpty() ? email.value() : name;
  }

  public UserId getId() {
    return id;
  }

  public Email getEmail() {
    return email;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public Set<UserRole> getRoles() {
    return roles;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getLastSeenAt() {
    return lastSeenAt;
  }

  public long getVersion() {
    return version;
  }

  public Instant getAuthUpdatedAt() {
    return authUpdatedAt;
  }

  public void markAuthUpdatedAt(Instant authUpdatedAt) {
    this.authUpdatedAt = Objects.requireNonNull(authUpdatedAt, "authUpdatedAt required");
  }

  private void requireNotBefore(Instant now) {
    Objects.requireNonNull(now, "now required");
    if (now.isBefore(updatedAt)) {
      throw new IllegalArgumentException("Update time cannot be before updatedAt");
    }
  }

  private static String normalizeName(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.length() > 100) {
      throw new IllegalArgumentException("Name is too long");
    }
    return trimmed;
  }

  private static Set<UserRole> normalizeRoles(Set<UserRole> value) {
    if (value == null || value.isEmpty()) {
      return Collections.unmodifiableSet(EnumSet.of(UserRole.DEFAULT));
    }
    return Collections.unmodifiableSet(EnumSet.copyOf(value));
  }
}
