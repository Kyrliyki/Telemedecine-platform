package com.telemed.user.application.port.input;

import com.telemed.user.application.dto.view.UserProfileView;
import java.util.Set;
import java.util.UUID;

public interface GetUserProfileUseCase {

  UserProfileView getProfile(UUID userId);

  UserProfileView getOrCreateFromToken(UUID userId, String email, Set<String> roles);
}
