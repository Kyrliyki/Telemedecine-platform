package com.telemed.user.presentation.rest.v1.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.telemed.user.application.dto.view.DoctorProfileView;
import com.telemed.user.application.dto.view.PageResult;
import com.telemed.user.application.service.DoctorProfileService;
import com.telemed.user.presentation.rest.v1.dto.request.RejectDoctorRequest;
import com.telemed.user.presentation.rest.v1.mapper.UserWebMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AdminDoctorControllerTest {

  private static final UUID DOCTOR_ID = UUID.randomUUID();
  private static final UUID ADMIN_ID = UUID.randomUUID();

  @Mock private DoctorProfileService service;

  private AdminDoctorController controller;

  @BeforeEach
  void setUp() {
    controller = new AdminDoctorController(service, new UserWebMapper());
  }

  @Test
  void listsPendingDoctors() {
    when(service.findAllForAdmin("PENDING", "cardio", 0, 20))
        .thenReturn(new PageResult<>(List.of(doctor("PENDING")), 1, 0, 20));

    var response = controller.list(0, 20, "PENDING", "cardio");

    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().verificationStatus()).isEqualTo("PENDING");
  }

  @Test
  void approvesDoctorAsAuthenticatedAdmin() {
    when(service.approve(DOCTOR_ID, ADMIN_ID)).thenReturn(doctor("VERIFIED"));

    var response = controller.approve(jwt(), DOCTOR_ID);

    assertThat(response.verificationStatus()).isEqualTo("VERIFIED");
    verify(service).approve(DOCTOR_ID, ADMIN_ID);
  }

  @Test
  void rejectsDoctorWithReason() {
    when(service.reject(DOCTOR_ID, ADMIN_ID, "bad license")).thenReturn(doctor("REJECTED"));

    var response = controller.reject(jwt(), DOCTOR_ID, new RejectDoctorRequest("bad license"));

    assertThat(response.verificationStatus()).isEqualTo("REJECTED");
    verify(service).reject(DOCTOR_ID, ADMIN_ID, "bad license");
  }

  private static Jwt jwt() {
    return Jwt.withTokenValue("token")
        .header("alg", "RS256")
        .subject(ADMIN_ID.toString())
        .issuedAt(Instant.parse("2026-01-01T10:00:00Z"))
        .expiresAt(Instant.parse("2026-01-01T11:00:00Z"))
        .build();
  }

  private static DoctorProfileView doctor(String verificationStatus) {
    Instant now = Instant.parse("2026-01-01T10:00:00Z");
    return new DoctorProfileView(
        DOCTOR_ID,
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
        verificationStatus,
        null,
        null,
        null);
  }
}
