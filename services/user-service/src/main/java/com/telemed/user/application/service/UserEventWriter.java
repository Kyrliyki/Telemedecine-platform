package com.telemed.user.application.service;

import com.telemed.common.events.IntegrationEventEnvelope;
import com.telemed.common.events.UserEventPayloads.DoctorProfileSnapshot;
import com.telemed.common.events.UserEventPayloads.DoctorRejectedPayload;
import com.telemed.common.events.UserEventPayloads.DoctorVerifiedPayload;
import com.telemed.common.events.UserEventPayloads.ProfileUpdatedPayload;
import com.telemed.common.events.UserEventTypes;
import com.telemed.common.outbox.factory.OutboxEventFactory;
import com.telemed.common.outbox.port.OutboxRepository;
import com.telemed.common.security.util.CorrelationContext;
import com.telemed.user.domain.model.DoctorProfile;
import com.telemed.user.domain.model.User;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class UserEventWriter {

  private final OutboxRepository outboxRepository;
  private final OutboxEventFactory outboxEventFactory;
  private final JsonMapper jsonMapper;

  public UserEventWriter(
      OutboxRepository outboxRepository,
      OutboxEventFactory outboxEventFactory,
      JsonMapper jsonMapper) {
    this.outboxRepository = outboxRepository;
    this.outboxEventFactory = outboxEventFactory;
    this.jsonMapper = jsonMapper;
  }

  public void profileUpdated(User user, DoctorProfile doctorProfile, Instant now) {
    write(
        UserEventTypes.PROFILE_UPDATED,
        new ProfileUpdatedPayload(
            user.getId().value(),
            user.getEmail().value(),
            user.getFirstName(),
            user.getLastName(),
            user.displayName(),
            user.getRoles().stream().map(Enum::name).sorted().toList(),
            user.getStatus().name(),
            now,
            doctorProfile == null ? null : snapshot(doctorProfile)),
        now);
  }

  public void doctorVerified(User user, DoctorProfile profile, UUID verifiedBy, Instant now) {
    write(
        UserEventTypes.DOCTOR_VERIFIED,
        new DoctorVerifiedPayload(
            user.getId().value(),
            user.getEmail().value(),
            profile.getVerificationStatus().name(),
            verifiedBy,
            profile.getVerifiedAt()),
        now);
  }

  public void doctorRejected(
      User user, DoctorProfile profile, UUID verifiedBy, String reason, Instant now) {
    write(
        UserEventTypes.DOCTOR_REJECTED,
        new DoctorRejectedPayload(
            user.getId().value(),
            user.getEmail().value(),
            profile.getVerificationStatus().name(),
            reason,
            verifiedBy,
            now),
        now);
  }

  private static DoctorProfileSnapshot snapshot(DoctorProfile profile) {
    return new DoctorProfileSnapshot(
        profile.getSpecialization(),
        profile.getBio(),
        profile.getYearsOfExperience(),
        profile.getLicenseNumber(),
        profile.isAcceptingPatients(),
        profile.getVerificationStatus().name(),
        profile.getVerifiedAt(),
        profile.getVerifiedBy());
  }

  private void write(String eventType, Object payload, Instant occurredAt) {
    try {
      String json = jsonMapper.writeValueAsString(payload);
      IntegrationEventEnvelope envelope =
          new IntegrationEventEnvelope(
              UUID.randomUUID(), eventType, occurredAt, CorrelationContext.current(), json, 1);
      outboxRepository.save(outboxEventFactory.toEntity(envelope));
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to create " + eventType + " event", exception);
    }
  }
}
