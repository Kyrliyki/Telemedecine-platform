package com.telemed.auth.domain.exception;

import com.telemed.auth.domain.model.Actor;
import com.telemed.auth.domain.valueobject.UserRole;

public final class RoleAssignmentNotAllowedException extends DomainException {

  public RoleAssignmentNotAllowedException(Actor actor, UserRole role) {
    super(
        "ROLE_ASSIGNMENT_NOT_ALLOWED",
        "Actor " + actor.userId() + " is not allowed to assign role " + role);
  }
}
