package com.telemed.schedule.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
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
      "schedule.user-service.validation-enabled=false"
    })
@AutoConfigureMockMvc
class ScheduleServiceApiIntegrationTest {

  private static final UUID DOCTOR_ID = UUID.randomUUID();
  private static final UUID PATIENT_ID = UUID.randomUUID();
  private static final UUID ADMIN_ID = UUID.randomUUID();

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
    registry.add("spring.datasource.hikari.schema", () -> "schedule_service");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "schedule_service");
    registry.add("spring.flyway.schemas", () -> "schedule_service");
    registry.add("spring.flyway.default-schema", () -> "schedule_service");
    registry.add("spring.rabbitmq.host", RABBIT::getHost);
    registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
    registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
    registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
  }

  @Autowired private MockMvc mockMvc;

  private static RequestPostProcessor withRole(UUID userId, String role) {
    return jwt()
        .jwt(jwt -> jwt.subject(userId.toString()).claim("roles", List.of(role)))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  private static RequestPostProcessor patient() {
    return withRole(PATIENT_ID, "PATIENT");
  }

  private static RequestPostProcessor doctor() {
    return withRole(DOCTOR_ID, "DOCTOR");
  }

  private static RequestPostProcessor admin() {
    return withRole(ADMIN_ID, "ADMIN");
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc
        .perform(
            get(
                "/api/v1/schedule/doctors/"
                    + DOCTOR_ID
                    + "/availability?from=2026-01-05&to=2026-01-06"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void patientCanReadAvailability() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/schedule/doctors/"
                    + DOCTOR_ID
                    + "/availability?from=2026-01-05&to=2026-01-06")
                .with(patient()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  void doctorReplacesAndReadsWorkingHours() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/schedule/working-hours")
                .with(doctor())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "entries": [
                        {
                          "dayOfWeek": 1,
                          "startTime": "09:00",
                          "endTime": "17:00",
                          "slotDurationMinutes": 30,
                          "bufferMinutes": 0,
                          "timezone": "UTC"
                        }
                      ],
                      "effectiveFrom": "2026-01-05"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].dayOfWeek").value(1));

    mockMvc
        .perform(get("/api/v1/schedule/working-hours").with(doctor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].startTime").value("09:00:00"));
  }

  @Test
  void adminWorkingHoursRequireAdminRole() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/admin/schedule/doctors/" + DOCTOR_ID + "/working-hours").with(admin()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/api/v1/admin/schedule/doctors/" + DOCTOR_ID + "/working-hours").with(patient()))
        .andExpect(status().isForbidden());
  }

  @Test
  void bulkAvailabilityIsAvailable() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/schedule/doctors/availability")
                .with(patient())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "doctorIds": ["%s"],
                      "from": "2026-01-05",
                      "to": "2026-01-06",
                      "limit": 10
                    }
                    """
                        .formatted(DOCTOR_ID)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.doctors." + DOCTOR_ID).isArray());
  }

  @Test
  void rejectsInvalidWorkingHours() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/schedule/working-hours")
                .with(doctor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"entries\":[],\"effectiveFrom\":\"2026-01-05\"}"))
        .andExpect(status().is4xxClientError());
  }
}
