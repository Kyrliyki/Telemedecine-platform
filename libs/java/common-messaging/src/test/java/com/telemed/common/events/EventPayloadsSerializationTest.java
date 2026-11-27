package com.telemed.common.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.telemed.common.events.AppointmentEventPayloads.AppointmentCreatedPayload;
import com.telemed.common.events.AuthEventPayloads.UserRegisteredPayload;
import com.telemed.common.events.EmrEventPayloads.MedicalRecordPayload;
import com.telemed.common.events.ScheduleEventPayloads.SlotReservedPayload;
import com.telemed.common.events.UserEventPayloads.DoctorProfileSnapshot;
import com.telemed.common.events.UserEventPayloads.ProfileUpdatedPayload;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class EventPayloadsSerializationTest {

  private final JsonMapper json = JsonMapper.builder().build();
  private final UUID id = UUID.randomUUID();
  private final Instant now = Instant.parse("2026-01-01T10:00:00Z");

  @Test
  void authPayloadUsesCamelCaseAndStableFieldNames() {
    String value =
        json.writeValueAsString(
            new UserRegisteredPayload(id, "a@example.com", List.of("PATIENT"), true, "PASSWORD"));

    assertThat(value)
        .contains("\"userId\":\"" + id + "\"")
        .contains("\"emailVerified\":true")
        .doesNotContain("user_id");
  }

  @Test
  void appointmentPayloadUsesCamelCase() {
    String value =
        json.writeValueAsString(
            new AppointmentCreatedPayload(id, UUID.randomUUID(), UUID.randomUUID(), now, now));

    assertThat(value)
        .contains("\"appointmentId\":\"" + id + "\"")
        .contains("\"startsAt\":")
        .doesNotContain("appointment_id");
  }

  @Test
  void scheduleAndEmrPayloadsSerialize() {
    String schedule =
        json.writeValueAsString(
            new SlotReservedPayload(
                id, UUID.randomUUID(), UUID.randomUUID(), now, now.plusSeconds(1800)));
    String emr =
        json.writeValueAsString(
            new MedicalRecordPayload(
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "DRAFT", now));

    assertThat(schedule).contains("\"slotId\":\"" + id + "\"");
    assertThat(emr).contains("\"recordId\":\"" + id + "\"").contains("\"occurredAt\":");
  }

  @Test
  void userProfileEventSerializesNestedDoctorProfile() {
    String value =
        json.writeValueAsString(
            new ProfileUpdatedPayload(
                id,
                "doctor@example.com",
                "Ivan",
                "Petrov",
                "Ivan Petrov",
                List.of("DOCTOR"),
                "ACTIVE",
                now,
                new DoctorProfileSnapshot(
                    "Cardiology",
                    "bio",
                    10,
                    "LIC-1",
                    true,
                    "VERIFIED",
                    now,
                    UUID.randomUUID())));

    assertThat(value)
        .contains("\"userId\":\"" + id + "\"")
        .contains("\"displayName\":\"Ivan Petrov\"")
        .contains("\"doctorProfile\":")
        .contains("\"verificationStatus\":\"VERIFIED\"");
  }
}
