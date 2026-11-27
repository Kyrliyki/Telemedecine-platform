package com.telemed.user.presentation.rest.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.telemed.user.application.dto.view.DoctorProfileView;
import com.telemed.user.application.dto.view.UserProfileView;
import com.telemed.user.application.port.input.GetUserProfileUseCase;
import com.telemed.user.domain.valueobject.UserRole;
import com.telemed.user.domain.valueobject.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternalUserControllerTest {

  @Mock private GetUserProfileUseCase useCase;

  @Test
  void returnsRolesStatusAndDoctorVerification() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.parse("2026-01-01T10:00:00Z");
    DoctorProfileView doctor =
        new DoctorProfileView(
            userId,
            "doctor@example.com",
            "Ivan",
            "Petrov",
            "Cardiology",
            "bio",
            10,
            "LIC-1",
            true,
            now,
            now,
            0,
            "VERIFIED",
            now,
            UUID.randomUUID(),
            null);
    when(useCase.getProfile(userId))
        .thenReturn(
            new UserProfileView(
                userId,
                "doctor@example.com",
                "Ivan",
                "Petrov",
                Set.of(UserRole.DOCTOR),
                UserStatus.ACTIVE,
                now,
                now,
                null,
                0,
                doctor));

    var response = new InternalUserController(useCase).get(userId);

    assertThat(response.userId()).isEqualTo(userId);
    assertThat(response.displayName()).isEqualTo("Ivan Petrov");
    assertThat(response.roles()).contains("DOCTOR");
    assertThat(response.doctorProfile().verificationStatus()).isEqualTo("VERIFIED");
  }
}
