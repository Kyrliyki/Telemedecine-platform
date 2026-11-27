package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.RegisterLocalCommand;
import com.telemed.auth.application.dto.view.UserRegistrationResult;

public interface RegisterLocalUserUseCase {
  UserRegistrationResult register(RegisterLocalCommand command);
}
