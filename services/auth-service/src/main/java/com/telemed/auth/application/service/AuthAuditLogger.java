package com.telemed.auth.application.service;

import com.telemed.auth.application.dto.query.AuthAuditQuery;
import com.telemed.auth.application.dto.view.AuthAuditPageView;
import com.telemed.auth.application.dto.view.AuthAuditView;
import com.telemed.auth.infrastructure.persistence.entity.AuthAuditLogEntity;
import com.telemed.auth.infrastructure.persistence.repository.JpaAuthAuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

@Component
public class AuthAuditLogger {

  private static final Logger log = LoggerFactory.getLogger("auth.audit");

  private final JpaAuthAuditLogRepository repository;
  private final JsonMapper jsonMapper;
  private final TransactionTemplate transactionTemplate;

  public AuthAuditLogger(
      JpaAuthAuditLogRepository repository,
      JsonMapper jsonMapper,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.jsonMapper = jsonMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public void record(
      UUID actorId, UUID targetUserId, String action, String outcome, Map<String, Object> details) {
    AuthAuditLogEntity entity = new AuthAuditLogEntity();
    entity.setId(UUID.randomUUID());
    entity.setActorId(actorId);
    entity.setTargetUserId(targetUserId);
    entity.setAction(action);
    entity.setOutcome(outcome);
    entity.setCreatedAt(Instant.now());
    HttpServletRequest request = currentRequest();
    if (request != null) {
      entity.setIpAddress(clientAddress(request));
      entity.setUserAgent(trim(request.getHeader("User-Agent"), 512));
    }
    entity.setRequestId(MDC.get("requestId"));
    entity.setCorrelationId(MDC.get("correlationId"));
    try {
      Map<String, Object> safeDetails = new LinkedHashMap<>();
      if (details != null) {
        details.forEach(
            (key, value) -> {
              if (value != null && !isSecret(key)) {
                safeDetails.put(key, value);
              }
            });
      }
      entity.setDetails(jsonMapper.writeValueAsString(safeDetails));
    } catch (Exception exception) {
      entity.setDetails("{}");
    }
    try {
      transactionTemplate.executeWithoutResult(status -> repository.save(entity));
      log.info(
          "action={} outcome={} actorId={} targetUserId={} requestId={} correlationId={}",
          action,
          outcome,
          actorId,
          targetUserId,
          entity.getRequestId(),
          entity.getCorrelationId());
    } catch (RuntimeException exception) {

      log.error(
          "Failed to persist auth audit action={} targetUserId={}",
          action,
          targetUserId,
          exception);
    }
  }

  public void loginSucceeded(UUID userId, String method) {
    record(userId, userId, "LOGIN_SUCCEEDED", "SUCCESS", Map.of("method", method));
  }

  public void loginFailed(UUID userId, String email, String reason) {
    record(null, userId, "LOGIN_FAILED", "FAILURE", Map.of("email", mask(email), "reason", reason));
  }

  public void logout(UUID userId) {
    record(userId, userId, "LOGOUT", "SUCCESS", Map.of());
  }

  public void tokenRefreshed(UUID userId) {
    record(userId, userId, "TOKEN_REFRESHED", "SUCCESS", Map.of());
  }

  public void refreshReuseDetected(UUID userId) {
    record(userId, userId, "TOKEN_REFRESH_REUSE", "FAILURE", Map.of());
  }

  public void blocked(UUID actorId, UUID targetUserId, String reason) {
    record(actorId, targetUserId, "USER_BLOCKED", "SUCCESS", Map.of("reason", safe(reason)));
  }

  public void unblocked(UUID actorId, UUID targetUserId) {
    record(actorId, targetUserId, "USER_UNBLOCKED", "SUCCESS", Map.of());
  }

  public void roleAssigned(UUID actorId, UUID targetUserId, String role) {
    record(actorId, targetUserId, "ROLE_ASSIGNED", "SUCCESS", Map.of("role", role));
  }

  public void roleRevoked(UUID actorId, UUID targetUserId, String role) {
    record(actorId, targetUserId, "ROLE_REVOKED", "SUCCESS", Map.of("role", role));
  }

  public void accessTokenRevoked(UUID targetUserId, String reason) {
    record(null, targetUserId, "ACCESS_TOKENS_REVOKED", "SUCCESS", Map.of("reason", reason));
  }

  public AuthAuditPageView search(AuthAuditQuery query) {
    org.springframework.data.jpa.domain.Specification<AuthAuditLogEntity> specification =
        (root, criteriaQuery, builder) -> {
          java.util.List<jakarta.persistence.criteria.Predicate> predicates =
              new java.util.ArrayList<>();
          if (query.targetUserId() != null) {
            predicates.add(builder.equal(root.get("targetUserId"), query.targetUserId()));
          }
          if (query.actorId() != null) {
            predicates.add(builder.equal(root.get("actorId"), query.actorId()));
          }
          if (query.action() != null && !query.action().isBlank()) {
            predicates.add(builder.equal(root.get("action"), query.action()));
          }
          if (query.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
          }
          if (query.to() != null) {
            predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), query.to()));
          }
          return builder.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    Page<AuthAuditLogEntity> page =
        repository.findAll(
            specification,
            PageRequest.of(
                query.page(),
                query.size(),
                org.springframework.data.domain.Sort.by(
                    org.springframework.data.domain.Sort.Direction.DESC, "createdAt")));
    return new AuthAuditPageView(
        page.getContent().stream().map(this::toView).toList(),
        page.getTotalElements(),
        page.getNumber(),
        page.getSize(),
        page.getTotalPages());
  }

  private AuthAuditView toView(AuthAuditLogEntity entity) {
    return new AuthAuditView(
        entity.getId(),
        entity.getActorId(),
        entity.getTargetUserId(),
        entity.getAction(),
        entity.getOutcome(),
        entity.getIpAddress(),
        entity.getUserAgent(),
        entity.getRequestId(),
        entity.getCorrelationId(),
        entity.getDetails(),
        entity.getCreatedAt());
  }

  private static boolean isSecret(String key) {
    String normalized = key.toLowerCase(java.util.Locale.ROOT);
    return normalized.contains("password")
        || normalized.contains("token")
        || normalized.contains("secret");
  }

  private static String mask(String email) {
    if (email == null || email.isBlank()) {
      return "***";
    }
    int at = email.indexOf('@');
    if (at <= 1) {
      return "***";
    }
    return email.charAt(0) + "***" + email.substring(at);
  }

  private static String safe(String value) {
    return value == null ? "" : trim(value, 240);
  }

  private static String trim(String value, int max) {
    if (value == null) {
      return null;
    }
    return value.length() <= max ? value : value.substring(0, max);
  }

  private static HttpServletRequest currentRequest() {
    var attributes = RequestContextHolder.getRequestAttributes();
    return attributes instanceof ServletRequestAttributes servletAttributes
        ? servletAttributes.getRequest()
        : null;
  }

  private static String clientAddress(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
