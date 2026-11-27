package com.telemed.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telemed.auth.application.dto.query.AdminUserQuery;
import com.telemed.auth.application.exception.UserNotFoundException;
import com.telemed.auth.application.port.output.AuthUserRepository;
import com.telemed.auth.application.port.output.PageResult;
import com.telemed.auth.domain.model.AuthUser;
import com.telemed.auth.domain.valueobject.AuthStatus;
import com.telemed.auth.domain.valueobject.Email;
import com.telemed.auth.domain.valueobject.PasswordHash;
import com.telemed.auth.domain.valueobject.UserId;
import com.telemed.auth.domain.valueobject.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserQueryServiceTest {

  private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

  @Mock AuthUserRepository authUserRepository;

  private AdminUserQueryService service;

  @BeforeEach
  void setUp() {
    service = new AdminUserQueryService(authUserRepository);
  }

  @Test
  void returnsPageWithoutFilters() {
    AuthUser user = user("first@example.com");
    PageResult<AuthUser> page = new PageResult<>(List.of(user), 1, 0, 20);
    when(authUserRepository.findAll(null, null, null, 0, 20)).thenReturn(page);

    PageResult<AuthUser> result = service.findAll(new AdminUserQuery(0, 20, null, null, null));

    assertThat(result.content()).containsExactly(user);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.totalPages()).isEqualTo(1);
  }

  @Test
  void parsesFilters() {
    AuthUser user = user("doctor@example.com");
    PageResult<AuthUser> page = new PageResult<>(List.of(user), 1, 2, 5);
    when(authUserRepository.findAll("doctor", AuthStatus.ACTIVE, UserRole.DOCTOR, 2, 5))
        .thenReturn(page);

    PageResult<AuthUser> result =
        service.findAll(new AdminUserQuery(2, 5, " doctor ", "active", "doctor"));

    assertThat(result.content()).containsExactly(user);
  }

  @Test
  void rejectsUnknownStatus() {
    assertThatThrownBy(() -> service.findAll(new AdminUserQuery(0, 20, null, "unknown", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown status");
  }

  @Test
  void rejectsUnknownRole() {
    assertThatThrownBy(() -> service.findAll(new AdminUserQuery(0, 20, null, null, "superuser")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Unknown role");
  }

  @Test
  void returnsUserById() {
    AuthUser user = user("doctor@example.com");
    when(authUserRepository.findById(user.id())).thenReturn(Optional.of(user));

    assertThat(service.findById(user.id().value())).isEqualTo(user);
  }

  @Test
  void throwsWhenUserDoesNotExist() {
    UserId id = UserId.newId();
    when(authUserRepository.findById(id)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById(id.value()))
        .isInstanceOf(UserNotFoundException.class)
        .hasMessageContaining(id.value().toString());
  }

  private static AuthUser user(String email) {
    return AuthUser.registerWithPassword(new Email(email), new PasswordHash("hash"), NOW);
  }
}
