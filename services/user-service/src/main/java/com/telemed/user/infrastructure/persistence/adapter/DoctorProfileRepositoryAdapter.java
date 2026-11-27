package com.telemed.user.infrastructure.persistence.adapter;

import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.application.port.output.DoctorProfileRepository;
import com.telemed.user.domain.model.DoctorProfile;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import com.telemed.user.infrastructure.persistence.entity.DoctorProfileEntity;
import com.telemed.user.infrastructure.persistence.mapper.DoctorProfileEntityMapper;
import com.telemed.user.infrastructure.persistence.repository.JpaDoctorProfileRepository;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class DoctorProfileRepositoryAdapter implements DoctorProfileRepository {

  private final JpaDoctorProfileRepository jpa;
  private final DoctorProfileEntityMapper mapper;

  public DoctorProfileRepositoryAdapter(
      JpaDoctorProfileRepository jpa, DoctorProfileEntityMapper mapper) {
    this.jpa = jpa;
    this.mapper = mapper;
  }

  @Override
  public DoctorProfile save(DoctorProfile profile) {
    DoctorProfileEntity saved = jpa.save(mapper.toEntity(profile));
    return mapper.toDomain(saved);
  }

  @Override
  public Optional<DoctorProfile> findByUserId(UserId userId) {
    return jpa.findById(userId.value()).map(mapper::toDomain);
  }

  @Override
  public PageResult<DoctorProfile> findAll(String specialization, int page, int size) {
    String filter = specialization == null ? "" : specialization.trim();
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
    var result = jpa.findActiveDoctors(filter, UserStatus.ACTIVE, UserRole.DOCTOR, pageable);

    return new PageResult<>(
        result.getContent().stream().map(mapper::toDomain).toList(),
        result.getTotalElements(),
        result.getNumber(),
        result.getSize());
  }

  @Override
  public PageResult<DoctorProfile> findAllForAdmin(
      String verificationStatus, String specialization, int page, int size) {
    String statusFilter = verificationStatus == null ? "" : verificationStatus.trim();
    String specializationFilter = specialization == null ? "" : specialization.trim();
    var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
    var result = jpa.findForAdmin(statusFilter, specializationFilter, UserRole.DOCTOR, pageable);
    return new PageResult<>(
        result.getContent().stream().map(mapper::toDomain).toList(),
        result.getTotalElements(),
        result.getNumber(),
        result.getSize());
  }

  @Override
  public void deleteByUserId(UserId userId) {
    if (jpa.existsById(userId.value())) {
      jpa.deleteById(userId.value());
    }
  }
}
