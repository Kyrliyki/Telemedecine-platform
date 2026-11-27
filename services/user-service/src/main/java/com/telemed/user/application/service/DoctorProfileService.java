package com.telemed.user.application.service;

import com.telemed.user.application.dto.command.UpsertDoctorProfileCommand;
import com.telemed.user.application.dto.view.DoctorProfileView;
import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.application.exception.DoctorProfileNotFoundException;
import com.telemed.user.application.exception.UserNotFoundException;
import com.telemed.user.application.mapper.UserViewMapper;
import com.telemed.user.application.port.input.GetDoctorProfileUseCase;
import com.telemed.user.application.port.input.UpsertDoctorProfileUseCase;
import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.DoctorProfileRepository;
import com.telemed.user.application.port.output.UserRepository;
import com.telemed.user.domain.model.DoctorProfile;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserStatus;
import com.telemed.user.infrastructure.audit.UserAuditLogger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DoctorProfileService implements GetDoctorProfileUseCase, UpsertDoctorProfileUseCase {

  private static final int MAX_PAGE_SIZE = 100;

  private final UserRepository userRepository;
  private final DoctorProfileRepository doctorProfileRepository;
  private final UserViewMapper viewMapper;
  private final Clock clock;
  private final UserAuditLogger audit;
  private final UserEventWriter eventWriter;

  public DoctorProfileService(
      UserRepository userRepository,
      DoctorProfileRepository doctorProfileRepository,
      UserViewMapper viewMapper,
      Clock clock,
      UserAuditLogger audit,
      UserEventWriter eventWriter) {
    this.userRepository = userRepository;
    this.doctorProfileRepository = doctorProfileRepository;
    this.viewMapper = viewMapper;
    this.clock = clock;
    this.audit = audit;
    this.eventWriter = eventWriter;
  }

  @Override
  public DoctorProfileView getByUserId(UUID userId) {
    User user = loadUser(userId);
    if (!user.isDoctor() || user.getStatus() != UserStatus.ACTIVE) {
      throw new DoctorProfileNotFoundException(userId);
    }
    DoctorProfile profile =
        doctorProfileRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new DoctorProfileNotFoundException(userId));
    if (!profile.isVerified()) {

      throw new DoctorProfileNotFoundException(userId);
    }
    return viewMapper.toDoctorView(user, profile);
  }

  @Override
  public DoctorProfileView getForOwner(UUID userId) {
    User user = loadUser(userId);
    if (!user.isDoctor()) {
      throw new DoctorProfileNotFoundException(userId);
    }
    DoctorProfile profile =
        doctorProfileRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new DoctorProfileNotFoundException(userId));
    return viewMapper.toDoctorView(user, profile);
  }

  public DoctorProfileView getInternal(UUID userId) {
    User user = loadUser(userId);
    DoctorProfile profile =
        doctorProfileRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new DoctorProfileNotFoundException(userId));
    return viewMapper.toDoctorView(user, profile);
  }

  @Override
  public PageResult<DoctorProfileView> findAll(String specialization, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);

    PageResult<DoctorProfile> profiles =
        doctorProfileRepository.findAll(specialization, safePage, safeSize);

    return mapPage(profiles);
  }

  public PageResult<DoctorProfileView> findAllForAdmin(
      String verificationStatus, String specialization, int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = size <= 0 ? 20 : Math.min(size, MAX_PAGE_SIZE);
    return mapPage(
        doctorProfileRepository.findAllForAdmin(
            normalizeStatus(verificationStatus), specialization, safePage, safeSize));
  }

  @Override
  @Transactional
  public DoctorProfileView upsert(UUID userId, UpsertDoctorProfileCommand command) {
    User user = loadUser(userId);
    if (!user.isDoctor()) {
      throw new AccessDeniedException("DOCTOR role is required");
    }

    DoctorProfile profile =
        doctorProfileRepository
            .findByUserId(user.getId())
            .map(
                existing -> {
                  existing.update(
                      command.specialization(),
                      command.bio(),
                      command.yearsOfExperience(),
                      command.licenseNumber(),
                      command.acceptingPatients(),
                      clock.now());
                  return existing;
                })
            .orElseGet(
                () ->
                    DoctorProfile.create(
                        user.getId(),
                        command.specialization(),
                        command.bio(),
                        command.yearsOfExperience(),
                        command.licenseNumber(),
                        command.acceptingPatients(),
                        clock.now()));

    profile.submitForVerification(clock.now());
    DoctorProfile saved = doctorProfileRepository.save(profile);
    eventWriter.profileUpdated(user, saved, saved.getUpdatedAt());
    audit.doctorProfileUpserted(userId);
    return viewMapper.toDoctorView(user, saved);
  }

  @Transactional
  public DoctorProfileView updateByAdmin(UUID userId, UpsertDoctorProfileCommand command) {
    User user = loadUser(userId);
    if (!user.isDoctor()) {
      throw new AccessDeniedException("DOCTOR role is required");
    }

    DoctorProfile profile =
        doctorProfileRepository
            .findByUserId(user.getId())
            .map(
                existing -> {
                  existing.update(
                      command.specialization(),
                      command.bio(),
                      command.yearsOfExperience(),
                      command.licenseNumber(),
                      command.acceptingPatients(),
                      clock.now());
                  return existing;
                })
            .orElseGet(
                () ->
                    DoctorProfile.create(
                        user.getId(),
                        command.specialization(),
                        command.bio(),
                        command.yearsOfExperience(),
                        command.licenseNumber(),
                        command.acceptingPatients(),
                        clock.now()));

    DoctorProfile saved = doctorProfileRepository.save(profile);
    eventWriter.profileUpdated(user, saved, saved.getUpdatedAt());
    audit.doctorProfileUpserted(userId);
    return viewMapper.toDoctorView(user, saved);
  }

  @Transactional
  public DoctorProfileView approve(UUID userId, UUID adminId) {
    User user = loadUser(userId);
    if (!user.isDoctor()) {
      throw new AccessDeniedException("DOCTOR role is required");
    }
    DoctorProfile profile =
        doctorProfileRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new DoctorProfileNotFoundException(userId));
    profile.approve(adminId, clock.now());
    DoctorProfile saved = doctorProfileRepository.save(profile);
    audit.doctorVerified(userId, adminId);
    eventWriter.doctorVerified(user, saved, adminId, saved.getUpdatedAt());
    eventWriter.profileUpdated(user, saved, saved.getUpdatedAt());
    return viewMapper.toDoctorView(user, saved);
  }

  @Transactional
  public DoctorProfileView reject(UUID userId, UUID adminId, String reason) {
    User user = loadUser(userId);
    if (!user.isDoctor()) {
      throw new AccessDeniedException("DOCTOR role is required");
    }
    DoctorProfile profile =
        doctorProfileRepository
            .findByUserId(user.getId())
            .orElseThrow(() -> new DoctorProfileNotFoundException(userId));
    profile.reject(adminId, reason, clock.now());
    DoctorProfile saved = doctorProfileRepository.save(profile);
    audit.doctorRejected(userId, adminId, reason);
    eventWriter.doctorRejected(user, saved, adminId, reason, saved.getUpdatedAt());
    eventWriter.profileUpdated(user, saved, saved.getUpdatedAt());
    return viewMapper.toDoctorView(user, saved);
  }

  private PageResult<DoctorProfileView> mapPage(PageResult<DoctorProfile> profiles) {
    List<UserId> userIds = profiles.content().stream().map(DoctorProfile::getUserId).toList();
    Map<UUID, User> users =
        userRepository.findAllByIds(userIds).stream()
            .collect(Collectors.toMap(user -> user.getId().value(), Function.identity()));

    List<DoctorProfileView> content =
        profiles.content().stream()
            .filter(profile -> users.containsKey(profile.getUserId().value()))
            .filter(
                profile -> {
                  User user = users.get(profile.getUserId().value());
                  return user.isDoctor() && user.getStatus() == UserStatus.ACTIVE;
                })
            .map(
                profile -> viewMapper.toDoctorView(users.get(profile.getUserId().value()), profile))
            .toList();

    return new PageResult<>(content, profiles.totalElements(), profiles.page(), profiles.size());
  }

  private User loadUser(UUID userId) {
    return userRepository
        .findById(new UserId(userId))
        .orElseThrow(() -> new UserNotFoundException(userId));
  }

  private static String normalizeStatus(String value) {
    return value == null || value.isBlank()
        ? null
        : value.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
