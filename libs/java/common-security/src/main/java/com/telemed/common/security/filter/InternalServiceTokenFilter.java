package com.telemed.common.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guard for service-to-service endpoints under {@code /internal/**}.
 *
 * <p>The caller must send the configured shared token. The check is fail-closed: if no token is
 * configured, every internal request is rejected. All other paths pass through unchanged.
 */
public final class InternalServiceTokenFilter extends OncePerRequestFilter {

  public static final String DEFAULT_HEADER = "X-Internal-Token";

  private final String expectedToken;
  private final String headerName;

  public InternalServiceTokenFilter(String expectedToken, String headerName) {
    this.expectedToken = expectedToken == null ? "" : expectedToken;
    this.headerName = (headerName == null || headerName.isBlank()) ? DEFAULT_HEADER : headerName;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith("/internal/")) {
      chain.doFilter(request, response);
      return;
    }
    if (expectedToken.isBlank() || !matches(request.getHeader(headerName))) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal service token");
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean matches(String provided) {
    if (provided == null || provided.isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        expectedToken.getBytes(StandardCharsets.UTF_8), provided.getBytes(StandardCharsets.UTF_8));
  }
}
