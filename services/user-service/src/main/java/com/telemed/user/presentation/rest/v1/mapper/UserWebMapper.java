package com.telemed.user.presentation.rest.v1.mapper;

import com.telemed.user.application.dto.command.UpdateUserProfileCommand;
import com.telemed.user.application.dto.command.UpsertDoctorProfileCommand;
import com.telemed.user.application.dto.view.DoctorProfileView;
import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.application.dto.view.UserProfileView;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.presentation.rest.v1.dto.request.UpdateProfileRequest;
import com.telemed.user.presentation.rest.v1.dto.request.UpsertDoctorProfileRequest;
import com.telemed.user.presentation.rest.v1.dto.response.DoctorPageResponse;
import com.telemed.user.presentation.rest.v1.dto.response.DoctorProfileResponse;
import com.telemed.user.presentation.rest.v1.dto.response.UserPageResponse;
import com.telemed.user.presentation.rest.v1.dto.response.UserProfileResponse;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class UserWebMapper {

  public UserProfileResponse toResponse(UserProfileView view) {
    return new UserProfileResponse(
        view.id(),
        view.email(),
        view.firstName(),
        view.lastName(),
        roleNames(view.roles()),
        view.status().name(),
        view.createdAt(),
        view.updatedAt(),
        view.lastSeenAt(),
        view.version(),
        view.doctorProfile() == null ? null : toResponse(view.doctorProfile()));
  }

  public DoctorProfileResponse toResponse(DoctorProfileView view) {
    return new DoctorProfileResponse(
        view.userId(),
        view.email(),
        view.firstName(),
        view.lastName(),
        view.specialization(),
        view.bio(),
        view.yearsOfExperience(),
        view.licenseNumber(),
        view.acceptingPatients(),
        view.createdAt(),
        view.updatedAt(),
        view.version(),
        view.verificationStatus(),
        view.verifiedAt(),
        view.verifiedBy(),
        view.rejectionReason());
  }

  public UserPageResponse toUserPageResponse(PageResult<UserProfileView> page) {
    return new UserPageResponse(
        page.content().stream().map(this::toResponse).toList(),
        page.totalElements(),
        page.page(),
        page.size(),
        page.totalPages());
  }

  public DoctorPageResponse toDoctorPageResponse(PageResult<DoctorProfileView> page) {
    return new DoctorPageResponse(
        page.content().stream().map(this::toResponse).toList(),
        page.totalElements(),
        page.page(),
        page.size(),
        page.totalPages());
  }

  public UpdateUserProfileCommand toCommand(UpdateProfileRequest request) {
    return new UpdateUserProfileCommand(request.firstName(), request.lastName());
  }

  public UpsertDoctorProfileCommand toCommand(UpsertDoctorProfileRequest request) {
    return new UpsertDoctorProfileCommand(
        request.specialization(),
        request.bio(),
        request.yearsOfExperience() == null ? 0 : request.yearsOfExperience(),
        request.licenseNumber(),
        request.acceptingPatients() == null || request.acceptingPatients());
  }

  private static Set<String> roleNames(Set<UserRole> roles) {
    return roles.stream()
        .map(UserRole::name)
        .sorted()
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }
}
