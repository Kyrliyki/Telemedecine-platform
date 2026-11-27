package com.telemed.user.application.port.input;

import com.telemed.user.application.dto.view.DoctorProfileView;
import com.telemed.user.application.dto.view.PageResult;
import java.util.UUID;

public interface GetDoctorProfileUseCase {

  DoctorProfileView getByUserId(UUID userId);

  DoctorProfileView getForOwner(UUID userId);

  PageResult<DoctorProfileView> findAll(String specialization, int page, int size);
}
