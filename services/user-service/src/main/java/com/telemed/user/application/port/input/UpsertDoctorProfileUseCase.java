package com.telemed.user.application.port.input;

import com.telemed.user.application.dto.command.UpsertDoctorProfileCommand;
import com.telemed.user.application.dto.view.DoctorProfileView;
import java.util.UUID;

public interface UpsertDoctorProfileUseCase {

  DoctorProfileView upsert(UUID userId, UpsertDoctorProfileCommand command);
}
