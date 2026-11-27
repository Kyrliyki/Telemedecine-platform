package com.telemed.schedule.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.telemed.schedule.infrastructure.client.UserDirectoryClient;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class UserValidationServiceTest {

  @Mock private UserDirectoryClient client;

  private UserValidationService service;

  @BeforeEach
  void setUp() {
    service = new UserValidationService(client);
  }

  @Test
  void acceptsActiveVerifiedDoctor() {
    UUID doctorId = UUID.randomUUID();
    when(client.enabled()).thenReturn(true);
    when(client.find(doctorId))
        .thenReturn(
            Optional.of(
                new UserDirectoryClient.InternalUser(
                    doctorId,
                    "doctor@example.com",
                    "Ivan",
                    "Petrov",
                    "Ivan Petrov",
                    Set.of("DOCTOR"),
                    "ACTIVE",
                    new UserDirectoryClient.DoctorProfile(
                        "Cardiology",
                        "LIC-1",
                        true,
                        "VERIFIED",
                        Instant.now(),
                        UUID.randomUUID()))));

    assertThatCode(() -> service.requireActiveDoctor(doctorId)).doesNotThrowAnyException();
  }

  @Test
  void rejectsUnverifiedDoctor() {
    UUID doctorId = UUID.randomUUID();
    when(client.enabled()).thenReturn(true);
    when(client.find(doctorId))
        .thenReturn(
            Optional.of(
                new UserDirectoryClient.InternalUser(
                    doctorId,
                    "doctor@example.com",
                    "Ivan",
                    "Petrov",
                    "Ivan Petrov",
                    Set.of("DOCTOR"),
                    "ACTIVE",
                    new UserDirectoryClient.DoctorProfile(
                        "Cardiology", "LIC-1", true, "PENDING", null, null))));

    assertThatThrownBy(() -> service.requireActiveDoctor(doctorId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("not verified");
  }

  @Test
  void rejectsBlockedPatient() {
    UUID patientId = UUID.randomUUID();
    when(client.enabled()).thenReturn(true);
    when(client.find(patientId))
        .thenReturn(
            Optional.of(
                new UserDirectoryClient.InternalUser(
                    patientId,
                    "patient@example.com",
                    "Anna",
                    "Ivanova",
                    "Anna Ivanova",
                    Set.of("PATIENT"),
                    "BLOCKED",
                    null)));

    assertThatThrownBy(() -> service.requireActivePatient(patientId))
        .isInstanceOf(AccessDeniedException.class)
        .hasMessageContaining("not active");
  }

  @Test
  void doesNothingWhenValidationIsDisabled() {
    when(client.enabled()).thenReturn(false);

    assertThatCode(() -> service.requireActiveDoctor(UUID.randomUUID())).doesNotThrowAnyException();
    assertThatCode(() -> service.requireActivePatient(UUID.randomUUID()))
        .doesNotThrowAnyException();
  }
}
