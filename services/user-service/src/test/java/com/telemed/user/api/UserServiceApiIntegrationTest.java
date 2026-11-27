package com.telemed.user.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.telemed.user.application.port.input.GetUserProfileUseCase;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
    properties = {
      "security.internal.enabled=false",
      "user.auth-service.reconciliation-enabled=false"
    })
@AutoConfigureMockMvc
class UserServiceApiIntegrationTest {

  private static final UUID PATIENT_ID = UUID.randomUUID();
  private static final UUID DOCTOR_ID = UUID.randomUUID();

  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("telemedicine")
          .withUsername("telemedicine")
          .withPassword("telemedicine");

  @Container
  static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management-alpine");

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.hikari.schema", () -> "user_service");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "user_service");
    registry.add("spring.flyway.schemas", () -> "user_service");
    registry.add("spring.flyway.default-schema", () -> "user_service");
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private GetUserProfileUseCase profileUseCase;

  @BeforeEach
  void seedProfiles() {
    profileUseCase.getOrCreateFromToken(PATIENT_ID, "patient@example.com", Set.of("PATIENT"));
    profileUseCase.getOrCreateFromToken(DOCTOR_ID, "doctor@example.com", Set.of("DOCTOR"));
  }

  private static RequestPostProcessor patient() {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject(PATIENT_ID.toString())
                    .claim("email", "patient@example.com")
                    .claim("roles", List.of("PATIENT")))
        .authorities(new SimpleGrantedAuthority("ROLE_PATIENT"));
  }

  private static RequestPostProcessor doctor() {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject(DOCTOR_ID.toString())
                    .claim("email", "doctor@example.com")
                    .claim("roles", List.of("DOCTOR")))
        .authorities(new SimpleGrantedAuthority("ROLE_DOCTOR"));
  }

  private static RequestPostProcessor admin() {
    return jwt()
        .jwt(
            jwt ->
                jwt.subject(UUID.randomUUID().toString())
                    .claim("email", "admin@example.com")
                    .claim("roles", List.of("ADMIN")))
        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void returnsCurrentProfileAndUpdatesNames() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/me").with(patient()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("patient@example.com"))
        .andExpect(jsonPath("$.roles[0]").value("PATIENT"));

    mockMvc
        .perform(
            patch("/api/v1/users/me")
                .with(patient())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"firstName\":\"Ivan\",\"lastName\":\"Petrov\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.firstName").value("Ivan"))
        .andExpect(jsonPath("$.lastName").value("Petrov"));
  }

  @Test
  void exposesDoctorCatalogToAuthenticatedUsers() throws Exception {
    mockMvc
        .perform(get("/api/v1/users/doctors?page=0&size=10").with(patient()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void enforcesAdminRoleOnUserList() throws Exception {
    mockMvc.perform(get("/api/v1/users?page=0&size=10").with(admin())).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/users?page=0&size=10").with(patient()))
        .andExpect(status().isForbidden());
  }

  @Test
  void doctorUpsertsAndReadsOwnProfile() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/me/doctor-profile")
                .with(doctor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "specialization": "Cardiology",
                      "bio": "Heart doctor",
                      "yearsOfExperience": 10,
                      "licenseNumber": "LIC-1",
                      "acceptingPatients": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.specialization").value("Cardiology"));

    mockMvc
        .perform(get("/api/v1/users/me/doctor-profile").with(doctor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.specialization").value("Cardiology"));
  }

  @Test
  void patientCannotUpsertDoctorProfile() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/me/doctor-profile")
                .with(patient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "specialization": "Cardiology",
                      "yearsOfExperience": 1,
                      "acceptingPatients": true
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void enforcesSelfOrPrivilegedProfileAccess() throws Exception {
    mockMvc.perform(get("/api/v1/users/" + PATIENT_ID).with(patient())).andExpect(status().isOk());

    mockMvc
        .perform(get("/api/v1/users/" + DOCTOR_ID).with(patient()))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/api/v1/users/" + DOCTOR_ID).with(doctor())).andExpect(status().isOk());
  }

  @Test
  void rejectsInvalidDoctorProfilePayload() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/users/me/doctor-profile")
                .with(doctor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"specialization\":\"\",\"yearsOfExperience\":999}"))
        .andExpect(status().is4xxClientError());
  }
}
