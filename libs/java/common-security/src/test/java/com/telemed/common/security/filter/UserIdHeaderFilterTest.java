package com.telemed.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class UserIdHeaderFilterTest {

  private final UserIdHeaderFilter filter = new UserIdHeaderFilter();

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void addsUserIdAndRolesForAuthenticatedUser() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "user-123", null, List.of(new SimpleGrantedAuthority("ROLE_DOCTOR"))));

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
    assertThat(wrapped).isNotNull();
    assertThat(wrapped.getHeader("X-User-Id")).isEqualTo("user-123");
    assertThat(wrapped.getHeader("X-User-Roles")).isEqualTo("DOCTOR");
  }

  @Test
  void doesNotAddHeadersForAnonymousUser() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new AnonymousAuthenticationToken(
                "anonymous-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    HttpServletRequest passed = (HttpServletRequest) chain.getRequest();
    assertThat(passed).isNotNull();
    assertThat(passed.getHeader("X-User-Id")).isNull();
    assertThat(passed.getHeader("X-User-Roles")).isNull();
  }

  @Test
  void doesNotFailWithoutAuthentication() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
  }
}
