package com.telemed.common.security.filter;

import com.telemed.common.security.util.HeaderNames;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.stream.Collectors;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class UserIdHeaderFilter extends OncePerRequestFilter {

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();

    boolean authenticated =
        auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);

    if (!authenticated) {
      chain.doFilter(request, response);
      return;
    }

    String userId = auth.getName();
    String roles =
        auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a.startsWith("ROLE_"))
            .map(a -> a.substring("ROLE_".length()))
            .collect(Collectors.joining(","));

    HttpServletRequestWrapper wrapper =
        new HttpServletRequestWrapper(request) {
          @Override
          public String getHeader(String name) {
            if (HeaderNames.USER_ID.equalsIgnoreCase(name)) return userId;
            if (HeaderNames.USER_ROLES.equalsIgnoreCase(name)) return roles;
            return super.getHeader(name);
          }

          @Override
          public Enumeration<String> getHeaders(String name) {
            if (HeaderNames.USER_ID.equalsIgnoreCase(name)) {
              return Collections.enumeration(java.util.List.of(userId));
            }
            if (HeaderNames.USER_ROLES.equalsIgnoreCase(name)) {
              return Collections.enumeration(java.util.List.of(roles));
            }
            return super.getHeaders(name);
          }

          @Override
          public Enumeration<String> getHeaderNames() {
            var names = java.util.Collections.list(super.getHeaderNames());
            names.add(HeaderNames.USER_ID);
            names.add(HeaderNames.USER_ROLES);
            return java.util.Collections.enumeration(names);
          }
        };

    chain.doFilter(wrapper, response);
  }
}
