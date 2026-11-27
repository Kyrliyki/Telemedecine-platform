package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.query.AdminUserQuery;
import com.telemed.auth.application.exception.UserNotFoundException;
import com.telemed.auth.application.port.input.AdminUserQueryUseCase;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.PageResult;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.AuthStatus;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserQueryService implements AdminUserQueryUseCase {

  private final AuthUserRepository authUserRepository;

  @Override
  public PageResult<AuthUser> findAll(AdminUserQuery query) {
    return authUserRepository.findAll(
        query.email(),
        parseStatus(query.status()),
        parseRole(query.role()),
        query.page(),
        query.size());
  }

  @Override
  public AuthUser findById(UUID id) {
    return authUserRepository
        .findById(new UserId(id))
        .orElseThrow(() -> new UserNotFoundException(id));
  }

  private static AuthStatus parseStatus(String value) {
    if (value == null) {
      return null;
    }
    try {
      return AuthStatus.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown status: " + value);
    }
  }

  private static UserRole parseRole(String value) {
    if (value == null) {
      return null;
    }
    try {
      return UserRole.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("Unknown role: " + value);
    }
  }
}
