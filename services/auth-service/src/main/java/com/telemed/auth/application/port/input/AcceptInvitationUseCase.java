package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.AcceptInvitationCommand;
import com.telemed.auth.application.dto.view.UserRegistrationResult;

public interface AcceptInvitationUseCase {
  UserRegistrationResult accept(AcceptInvitationCommand command);
}
