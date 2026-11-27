package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.AssignRoleCommand;

public interface AssignRoleUseCase {
  void assignRole(AssignRoleCommand command);

  void revokeRole(AssignRoleCommand command);
}
