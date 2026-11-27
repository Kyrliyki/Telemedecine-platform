package com.telemed.common.security.filter;

import com.telemed.common.security.jwt.JwtAuthenticationConverter;
import com.telemed.common.security.jwt.TokenValidator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTH_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtDecoder jwtDecoder;
  private final TokenValidator tokenValidator;
  private final JwtAuthenticationConverter converter;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader(AUTH_HEADER);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }

    try {
      Jwt jwt = jwtDecoder.decode(header.substring(BEARER_PREFIX.length()));
      tokenValidator.validate(jwt);
      SecurityContextHolder.getContext().setAuthentication(converter.convert(jwt));
    } catch (JwtException ex) {
      SecurityContextHolder.clearContext();
    } catch (RuntimeException ex) {
      SecurityContextHolder.clearContext();
      response.sendError(
          HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Authentication backend unavailable");
      return;
    }

    chain.doFilter(request, response);
  }
}
