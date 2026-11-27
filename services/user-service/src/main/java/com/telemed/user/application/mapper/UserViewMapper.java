package com.telemed.user.application.mapper;

import com.telemed.user.application.dto.view.DoctorProfileView;
import com.telemed.user.application.dto.view.UserProfileView;
import com.telemed.user.domain.model.DoctorProfile;
import com.telemed.user.domain.model.User;
import org.springframework.stereotype.Component;

@Component
public class UserViewMapper {

  public UserProfileView toView(User user, DoctorProfile doctorProfile) {
    return new UserProfileView(
        user.getId().value(),
        user.getEmail().value(),
        user.getFirstName(),
        user.getLastName(),
        user.getRoles(),
        user.getStatus(),
        user.getCreatedAt(),
        user.getUpdatedAt(),
        user.getLastSeenAt(),
        user.getVersion(),
        doctorProfile == null ? null : toDoctorView(user, doctorProfile));
  }

  public DoctorProfileView toDoctorView(User user, DoctorProfile profile) {
    return new DoctorProfileView(
        profile.getUserId().value(),
        user.getEmail().value(),
        user.getFirstName(),
        user.getLastName(),
        profile.getSpecialization(),
        profile.getBio(),
        profile.getYearsOfExperience(),
        profile.getLicenseNumber(),
        profile.isAcceptingPatients(),
        profile.getCreatedAt(),
        profile.getUpdatedAt(),
        profile.getVersion(),
        profile.getVerificationStatus().name(),
        profile.getVerifiedAt(),
        profile.getVerifiedBy(),
        profile.getRejectionReason());
  }
}
