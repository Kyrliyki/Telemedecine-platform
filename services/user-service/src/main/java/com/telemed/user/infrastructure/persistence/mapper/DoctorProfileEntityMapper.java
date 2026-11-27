package com.telemed.user.infrastructure.persistence.mapper;

import com.telemed.user.domain.model.DoctorProfile;
import com.telemed.user.domain.valueobject.DoctorVerificationStatus;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.infrastructure.persistence.entity.DoctorProfileEntity;
import org.springframework.stereotype.Component;

@Component
public class DoctorProfileEntityMapper {

  public DoctorProfile toDomain(DoctorProfileEntity entity) {
    return DoctorProfile.rehydrate(
        new UserId(entity.getUserId()),
        entity.getSpecialization(),
        entity.getBio(),
        entity.getYearsExperience(),
        entity.getLicenseNumber(),
        entity.isAcceptingPatients(),
        parseStatus(entity.getVerificationStatus()),
        entity.getVerifiedAt(),
        entity.getVerifiedBy(),
        entity.getRejectionReason(),
        entity.getCreatedAt(),
        entity.getUpdatedAt(),
        entity.getVersion());
  }

  public DoctorProfileEntity toEntity(DoctorProfile profile) {
    DoctorProfileEntity entity = new DoctorProfileEntity();
    entity.setUserId(profile.getUserId().value());
    entity.setSpecialization(profile.getSpecialization());
    entity.setBio(profile.getBio());
    entity.setYearsExperience(profile.getYearsOfExperience());
    entity.setLicenseNumber(profile.getLicenseNumber());
    entity.setAcceptingPatients(profile.isAcceptingPatients());
    entity.setVerificationStatus(profile.getVerificationStatus().name());
    entity.setVerifiedAt(profile.getVerifiedAt());
    entity.setVerifiedBy(profile.getVerifiedBy());
    entity.setRejectionReason(profile.getRejectionReason());
    entity.setCreatedAt(profile.getCreatedAt());
    entity.setUpdatedAt(profile.getUpdatedAt());
    entity.setVersion(profile.getVersion());
    return entity;
  }

  private static DoctorVerificationStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return DoctorVerificationStatus.PENDING;
    }
    try {
      return DoctorVerificationStatus.valueOf(value.toUpperCase(java.util.Locale.ROOT));
    } catch (IllegalArgumentException exception) {
      return DoctorVerificationStatus.PENDING;
    }
  }
}
