package com.telemed.user.application.port.output;

import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.domain.model.DoctorProfile;
import com.telemed.user.domain.valueobject.UserId;
import java.util.Optional;

public interface DoctorProfileRepository {

  DoctorProfile save(DoctorProfile profile);

  Optional<DoctorProfile> findByUserId(UserId userId);

  PageResult<DoctorProfile> findAll(String specialization, int page, int size);

  PageResult<DoctorProfile> findAllForAdmin(
      String verificationStatus, String specialization, int page, int size);

  void deleteByUserId(UserId userId);
}
