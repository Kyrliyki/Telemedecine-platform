package com.telemed.common.security.util;

import java.util.UUID;

public final class CorrelationContext {

  private static final String FALLBACK = "no-correlation-id";
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private CorrelationContext() {}

  public static void set(String correlationId) {
    if (correlationId == null || correlationId.isBlank()) {
      throw new IllegalArgumentException("Correlation ID cannot be null or blank");
    }
    CURRENT.set(correlationId);
  }

  public static String current() {
    String value = CURRENT.get();
    if (value != null) {
      return value;
    }
    String mdcValue = org.slf4j.MDC.get("correlationId");
    return mdcValue != null ? mdcValue : FALLBACK;
  }

  public static String getOrGenerate() {
    String value = CURRENT.get();
    if (value != null) {
      return value;
    }
    String mdcValue = org.slf4j.MDC.get("correlationId");
    return mdcValue != null ? mdcValue : UUID.randomUUID().toString();
  }

  public static void clear() {
    CURRENT.remove();
  }
}
