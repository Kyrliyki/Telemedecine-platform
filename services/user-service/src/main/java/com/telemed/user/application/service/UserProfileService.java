package com.telemed.user.application.service;

import com.telemed.user.application.dto.command.UpdateUserProfileCommand;
import com.telemed.user.application.dto.query.AdminUserQuery;
import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.application.dto.view.UserProfileView;
import com.telemed.user.application.exception.EmailAlreadyInUseException;
import com.telemed.user.application.exception.UserNotFoundException;
import com.telemed.user.application.mapper.UserViewMapper;
import com.telemed.user.application.port.input.GetUserProfileUseCase;
import com.telemed.user.application.port.input.ListUsersUseCase;
import com.telemed.user.application.port.input.UpdateUserProfileUseCase;
import com.telemed.user.application.port.output.Clock;
import com.telemed.user.application.port.output.DoctorProfileRepository;
import com.telemed.user.application.port.output.UserRepository;
import com.telemed.user.domain.model.DoctorProfile;
import com.telemed.user.domain.model.User;
import com.telemed.user.domain.valueobject.Email;
import com.telemed.user.domain.valueobject.UserId;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import com.telemed.user.infrastructure.audit.UserAuditLogger;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserProfileService
    implements GetUserProfileUseCase, UpdateUserProfileUseCase, ListUsersUseCase {

  private static final int MAX_PAGE_SIZE = 100;

  private final UserRepository userRepository;
  private final DoctorProfileRepository doctorProfileRepository;
  private final UserViewMapper viewMapper;
  private final Clock clock;
  private final UserAuditLogger audit;
  private final UserEventWriter eventWriter;

  public UserProfileService(
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
  public UserProfileView getProfile(UUID userId) {
    User user = load(userId);
    return viewMapper.toView(user, doctorProfile(user));
  }

  @Override
  @Transactional
  public UserProfileView getOrCreateFromToken(UUID userId, String email, Set<String> roles) {
    User existing = userRepository.findById(new UserId(userId)).orElse(null);
    if (existing != null) {
      return viewMapper.toView(existing, doctorProfile(existing));
    }

    if (email == null || email.isBlank()) {
      throw new AccessDeniedException("JWT has no email claim");
    }
    Email parsedEmail = new Email(email);
    if (userRepository.existsByEmail(parsedEmail)) {
      throw new EmailAlreadyInUseException(parsedEmail.value());
    }

    User created = User.fromAuth(new UserId(userId), parsedEmail, parseRoles(roles), clock.now());
    User saved = userRepository.save(created);
    return viewMapper.toView(saved, null);
  }

  @Override
  @Transactional
  public UserProfileView updateProfile(UUID userId, UpdateUserProfileCommand command) {
    User user = load(userId);
    user.updateProfile(command.firstName(), command.lastName(), clock.now());
    User saved = userRepository.save(user);
    eventWriter.profileUpdated(saved, doctorProfile(saved), saved.getUpdatedAt());
    audit.profileUpdated(userId);
    return viewMapper.toView(saved, doctorProfile(saved));
  }

  @Override
  public PageResult<UserProfileView> findAll(AdminUserQuery query) {
    int page = Math.max(query.page(), 0);
    int size = normalizeSize(query.size());
    audit.adminUserList(query.email(), query.role(), query.status());
    PageResult<User> users =
        userRepository.findAll(
            query.email(), parseRole(query.role()), parseStatus(query.status()), page, size);
    List<UserProfileView> content =
        users.content().stream().map(user -> viewMapper.toView(user, null)).toList();
    return new PageResult<>(content, users.totalElements(), users.page(), users.size());
  }

  private User load(UUID userId) {
    return userRepository
        .findById(new UserId(userId))
        .orElseThrow(() -> new UserNotFoundException(userId));
  }

  private DoctorProfile doctorProfile(User user) {
    if (!user.isDoctor()) {
      return null;
    }
    return doctorProfileRepository.findByUserId(user.getId()).orElse(null);
  }

  private static int normalizeSize(int size) {
    if (size <= 0) {
      return 20;
    }
    return Math.min(size, MAX_PAGE_SIZE);
  }

  private static Set<UserRole> parseRoles(Set<String> roles) {
    EnumSet<UserRole> parsed = EnumSet.noneOf(UserRole.class);
    if (roles != null) {
      for (String role : roles) {
        try {
          parsed.add(UserRole.valueOf(role.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {

        }
      }
    }
    if (parsed.isEmpty()) {
      parsed.add(UserRole.DEFAULT);
    }
    return parsed;
  }

  private static UserRole parseRole(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UserRole.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown role: " + value);
    }
  }

  private static UserStatus parseStatus(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return UserStatus.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown status: " + value);
    }
  }
}
