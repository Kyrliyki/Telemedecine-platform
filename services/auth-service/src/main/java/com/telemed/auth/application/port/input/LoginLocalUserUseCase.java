package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.LoginLocalCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;

public interface LoginLocalUserUseCase {
  TokenPairResult login(LoginLocalCommand command);
}
