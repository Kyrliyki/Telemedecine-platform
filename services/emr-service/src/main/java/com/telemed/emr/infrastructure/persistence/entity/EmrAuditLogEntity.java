package com.telemed.emr.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "emr_audit_log")
public class EmrAuditLogEntity {

  @Id private UUID id;

  @Column(name = "record_id")
  private UUID recordId;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "request_id", length = 128)
  private String requestId;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @Column(columnDefinition = "text")
  private String details;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getRecordId() {
    return recordId;
  }

  public void setRecordId(UUID recordId) {
    this.recordId = recordId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public void setActorId(UUID actorId) {
    this.actorId = actorId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getCorrelationId() {
    return correlationId;
  }

  public void setCorrelationId(String correlationId) {
    this.correlationId = correlationId;
  }

  public String getDetails() {
    return details;
  }

  public void setDetails(String details) {
    this.details = details;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
