package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.VerifyEmailCommand;

public interface VerifyEmailUseCase {
  void verify(VerifyEmailCommand command);
}
