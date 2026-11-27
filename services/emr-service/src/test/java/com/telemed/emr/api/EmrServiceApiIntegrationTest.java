package com.telemed.emr.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
      "emr.require-appointment=false",
      "emr.appointment-service.validation-enabled=false",
      "emr.file-service.validation-enabled=false"
    })
@AutoConfigureMockMvc
class EmrServiceApiIntegrationTest {

  private static final UUID DOCTOR_ID = UUID.randomUUID();
  private static final UUID PATIENT_ID = UUID.randomUUID();
  private static final UUID OTHER_PATIENT_ID = UUID.randomUUID();

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
    registry.add("spring.datasource.hikari.schema", () -> "emr");
    registry.add("spring.jpa.properties.hibernate.default_schema", () -> "emr");
    registry.add("spring.flyway.schemas", () -> "emr");
    registry.add("spring.flyway.default-schema", () -> "emr");
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

  private static RequestPostProcessor doctor() {
    return withRole(DOCTOR_ID, "DOCTOR");
  }

  private static RequestPostProcessor patient() {
    return withRole(PATIENT_ID, "PATIENT");
  }

  private static RequestPostProcessor otherPatient() {
    return withRole(OTHER_PATIENT_ID, "PATIENT");
  }

  @Test
  void requiresAuthentication() throws Exception {
    mockMvc.perform(get("/api/v1/emr/me/records")).andExpect(status().isUnauthorized());
  }

  @Test
  void doctorCreatesAndReadsRecord() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/emr/records")
                    .with(doctor())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "patientId": "%s",
                          "chiefComplaint": "Headache",
                          "diagnosisCode": "G43",
                          "diagnosisText": "Migraine",
                          "notes": "Follow up in two weeks"
                        }
                        """
                            .formatted(PATIENT_ID)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patientId").value(PATIENT_ID.toString()))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String recordId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(get("/api/v1/emr/records/" + recordId).with(doctor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.diagnosisText").value("Migraine"));

    mockMvc
        .perform(get("/api/v1/emr/patients/" + PATIENT_ID + "/records").with(doctor()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].id").value(recordId));
  }

  @Test
  void patientCannotCreateRecord() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/emr/records")
                .with(patient())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"patientId\":\"" + PATIENT_ID + "\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void patientCanListOwnRecords() throws Exception {
    mockMvc
        .perform(get("/api/v1/emr/me/records").with(patient()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isArray());
  }

  @Test
  void otherPatientCannotReadRecord() throws Exception {
    String body =
        mockMvc
            .perform(
                post("/api/v1/emr/records")
                    .with(doctor())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientId\":\"" + PATIENT_ID + "\",\"notes\":\"private\"}"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    String recordId = JsonPath.read(body, "$.id");

    mockMvc
        .perform(get("/api/v1/emr/records/" + recordId).with(otherPatient()))
        .andExpect(status().isForbidden());
  }

  @Test
  void rejectsRecordWithoutPatientId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/emr/records")
                .with(doctor())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"notes\":\"no patient\"}"))
        .andExpect(status().is4xxClientError());
  }
}
