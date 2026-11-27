package com.telemed.auth.application.port.input;

import com.telemed.auth.application.dto.command.OAuthLinkConfirmationCommand;

public interface OAuthLinkConfirmationUseCase {

  void confirm(OAuthLinkConfirmationCommand command);
}
