package com.telemed.user.infrastructure.audit;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class UserAuditLogger {

  private static final Logger log = LoggerFactory.getLogger("user.audit");

  public void profileUpdated(UUID userId) {
    log.info("action=profile.updated subjectId={}", userId);
  }

  public void identityCreated(UUID userId) {
    log.info("action=identity.created subjectId={}", userId);
  }

  public void identityRolesChanged(UUID userId) {
    log.info("action=identity.roles.changed subjectId={}", userId);
  }

  public void identityEmailChanged(UUID userId) {
    log.info("action=identity.email.changed subjectId={}", userId);
  }

  public void identityStatusChanged(UUID userId, String status) {
    log.info("action=identity.status.changed subjectId={} status={}", userId, status);
  }

  public void doctorProfileUpserted(UUID userId) {
    log.info("action=doctor_profile.upserted subjectId={}", userId);
  }

  public void doctorVerified(UUID userId, UUID adminId) {
    log.info("action=doctor.verified subjectId={} actorId={}", userId, adminId);
  }

  public void doctorRejected(UUID userId, UUID adminId, String reason) {
    log.info(
        "action=doctor.rejected subjectId={} actorId={} reasonPresent={}",
        userId,
        adminId,
        reason != null && !reason.isBlank());
  }

  public void dlqReplay(int replayed, int failed) {
    log.info("action=dlq.replay replayed={} failed={}", replayed, failed);
  }

  public void adminUserList(String email, String role, String status) {
    log.info(
        "action=admin.users.list emailFilter={} roleFilter={} statusFilter={}",
        email == null ? "" : email,
        role == null ? "" : role,
        status == null ? "" : status);
  }
}
