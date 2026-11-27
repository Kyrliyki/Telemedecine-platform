package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.CreateUserByAdminCommand;
import com.telemed.auth.application.dto.view.UserRegistrationResult;

public interface CreateUserByAdminUseCase {
  UserRegistrationResult createByAdmin(CreateUserByAdminCommand command);
}
