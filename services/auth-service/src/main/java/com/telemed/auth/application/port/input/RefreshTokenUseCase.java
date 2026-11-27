package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.RefreshTokenCommand;
import com.telemed.auth.application.dto.view.TokenPairResult;

public interface RefreshTokenUseCase {
  TokenPairResult refresh(RefreshTokenCommand command);
}
