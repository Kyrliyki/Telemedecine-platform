package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.BlockUserCommand;

public interface BlockUserUseCase {
  void block(BlockUserCommand command);

  void unblock(BlockUserCommand command);
}
