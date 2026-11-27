package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.LoginWithOAuthCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;

public interface LoginWithOAuthUseCase {
  TokenPairResult loginWithGoogle(LoginWithOAuthCommand command);
}
