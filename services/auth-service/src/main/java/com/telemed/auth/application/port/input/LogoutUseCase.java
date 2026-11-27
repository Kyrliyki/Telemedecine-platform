package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.LogoutCommand;

public interface LogoutUseCase {
  void logout(LogoutCommand command);
}
