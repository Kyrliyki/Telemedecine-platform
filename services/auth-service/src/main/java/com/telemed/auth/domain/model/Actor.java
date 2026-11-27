package com.telemed.auth.domain.model;

import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.util.Objects;
import java.util.Set;

public record Actor(UserId userId, Set<UserRole> roles, boolean system) {
  public Actor {
    Objects.requireNonNull(roles, "Actor.roles must not be null");
    roles = Set.copyOf(roles);
  }

  public static Actor systemActor() {
    return new Actor(null, Set.of(), true);
  }

  public static Actor user(UserId id, Set<UserRole> roles) {
    return new Actor(Objects.requireNonNull(id), roles, false);
  }

  public boolean hasRole(UserRole role) {
    return roles.contains(role);
  }

  public boolean isAdmin() {
    return hasRole(UserRole.ADMIN);
  }
}
