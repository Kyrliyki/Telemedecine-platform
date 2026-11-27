package com.telemed.auth.presentation.rest.v1.controller;

import com.telemed.auth.application.dto.query.AuthAuditQuery;
import com.telemed.auth.application.dto.view.AuthAuditPageView;
import com.telemed.auth.application.service.AuthAuditLogger;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/auth/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAuthAuditController {

  private final AuthAuditLogger audit;

  public AdminAuthAuditController(AuthAuditLogger audit) {
    this.audit = audit;
  }

  @GetMapping
  public AuthAuditPageView search(
      @RequestParam(required = false) UUID targetUserId,
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) String action,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return audit.search(
        new AuthAuditQuery(targetUserId, actorId, normalize(action), from, to, page, size));
  }

  private static String normalize(String action) {
    return action == null || action.isBlank()
        ? null
        : action.trim().toUpperCase(java.util.Locale.ROOT);
  }
}
