package com.telemed.user.application.port.input;

import com.telemed.user.application.dto.command.UpdateUserProfileCommand;
import com.telemed.user.application.dto.view.UserProfileView;
import java.util.UUID;

public interface UpdateUserProfileUseCase {

  UserProfileView updateProfile(UUID userId, UpdateUserProfileCommand command);
}
