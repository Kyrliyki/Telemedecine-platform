package com.telemed.emr.application.service;

import com.telemed.emr.application.dto.view.EmrAuditView;
import com.telemed.emr.infrastructure.persistence.EmrAuditWriter;
import com.telemed.emr.infrastructure.persistence.entity.EmrAuditLogEntity;
import com.telemed.emr.infrastructure.persistence.repository.JpaEmrAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

@Component
public class EmrAuditLogger {

  private static final Logger log = LoggerFactory.getLogger("emr.audit");

  private final JpaEmrAuditLogRepository repository;
  private final EmrAuditWriter writer;
  private final JsonMapper jsonMapper;

  public EmrAuditLogger(
      JpaEmrAuditLogRepository repository, EmrAuditWriter writer, JsonMapper jsonMapper) {
    this.repository = repository;
    this.writer = writer;
    this.jsonMapper = jsonMapper;
  }

  public void recordCreated(UUID recordId, UUID patientId, UUID doctorId) {
    write(
        recordId, doctorId, "RECORD_CREATED", Map.of("patientId", patientId, "doctorId", doctorId));
  }

  public void recordViewed(UUID recordId, UUID viewerId) {
    write(recordId, viewerId, "RECORD_VIEWED", Map.of());
  }

  public void recordUpdated(UUID recordId, UUID actorId) {
    write(recordId, actorId, "RECORD_UPDATED", Map.of());
  }

  public void recordFinalized(UUID recordId, UUID doctorId) {
    write(recordId, doctorId, "RECORD_FINALIZED", Map.of());
  }

  public void recordAmended(UUID recordId, UUID doctorId) {
    write(recordId, doctorId, "RECORD_AMENDED", Map.of());
  }

  public void recordDeleted(UUID recordId, UUID patientId, UUID doctorId) {
    write(recordId, null, "RECORD_DELETED", Map.of("patientId", patientId, "doctorId", doctorId));
  }

  public void recordAdminChange(UUID recordId, UUID actorId, String action) {
    write(recordId, actorId, action, Map.of());
  }

  public void recordPurged(UUID recordId) {
    write(recordId, null, "RECORD_PURGED", Map.of());
  }

  public void accessDenied(UUID recordId, UUID actorId, String reason) {
    write(recordId, actorId, "ACCESS_DENIED", Map.of("reason", reason));
  }

  public List<EmrAuditView> forRecord(UUID recordId) {
    return repository.findAllByRecordIdOrderByCreatedAtAsc(recordId).stream()
        .map(this::toView)
        .toList();
  }

  private void write(UUID recordId, UUID actorId, String action, Map<String, Object> details) {
    EmrAuditLogEntity entity = new EmrAuditLogEntity();
    entity.setId(UUID.randomUUID());
    entity.setRecordId(recordId);
    entity.setActorId(actorId);
    entity.setAction(action);
    entity.setCreatedAt(Instant.now());
    entity.setIpAddress(remoteAddress());
    entity.setRequestId(MDC.get("requestId"));
    entity.setCorrelationId(MDC.get("correlationId"));
    try {
      Map<String, Object> enriched = new LinkedHashMap<>(details);
      entity.setDetails(jsonMapper.writeValueAsString(enriched));
    } catch (Exception exception) {
      entity.setDetails("{}");
    }
    writer.save(entity);
    log.info(
        "action={} recordId={} actorId={} requestId={} correlationId={}",
        action,
        recordId,
        actorId,
        entity.getRequestId(),
        entity.getCorrelationId());
  }

  private EmrAuditView toView(EmrAuditLogEntity entity) {
    return new EmrAuditView(
        entity.getId(),
        entity.getRecordId(),
        entity.getActorId(),
        entity.getAction(),
        entity.getIpAddress(),
        entity.getRequestId(),
        entity.getCorrelationId(),
        entity.getDetails(),
        entity.getCreatedAt());
  }

  private static String remoteAddress() {
    var attributes = RequestContextHolder.getRequestAttributes();
    if (attributes instanceof ServletRequestAttributes servletAttributes) {
      HttpServletRequest request = servletAttributes.getRequest();
      String forwarded = request.getHeader("X-Forwarded-For");
      if (forwarded != null && !forwarded.isBlank()) {
        return forwarded.split(",")[0].trim();
      }
      return request.getRemoteAddr();
    }
    return null;
  }
}
