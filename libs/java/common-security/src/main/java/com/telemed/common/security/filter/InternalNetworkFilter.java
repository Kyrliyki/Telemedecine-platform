package com.telemed.common.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Optional network boundary for internal services. When enabled, requests whose remote address is
 * outside the configured CIDRs are rejected before any authentication or header trust logic runs.
 *
 * <p>Disabled by default ({@code security.internal.enabled=false}).
 */
public final class InternalNetworkFilter extends OncePerRequestFilter {

  private final List<IpAddressMatcher> allowedNetworks;

  public InternalNetworkFilter(List<String> allowedCidrs) {
    if (allowedCidrs == null) {
      throw new IllegalArgumentException("allowedCidrs must not be null");
    }
    this.allowedNetworks = allowedCidrs.stream().map(IpAddressMatcher::new).toList();
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (allowedNetworks.isEmpty() || !isAllowed(request)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden");
      return;
    }
    chain.doFilter(request, response);
  }

  private boolean isAllowed(HttpServletRequest request) {
    String remoteAddress = request.getRemoteAddr();
    if (remoteAddress == null || remoteAddress.isBlank()) {
      return false;
    }
    return allowedNetworks.stream().anyMatch(matcher -> matcher.matches(remoteAddress));
  }
}
