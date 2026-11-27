package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.InviteUserCommand;
import com.telemed.auth.application.dto.view.InvitationResult;

public interface InviteUserUseCase {
  InvitationResult invite(InviteUserCommand command);
}
