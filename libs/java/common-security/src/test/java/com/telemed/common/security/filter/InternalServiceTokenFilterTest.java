package com.telemed.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalServiceTokenFilterTest {

  private final FilterChain chain = mock(FilterChain.class);

  @Test
  void allowsInternalRequestWithValidToken() throws Exception {
    var filter = new InternalServiceTokenFilter("secret", "X-Internal-Token");
    var request = new MockHttpServletRequest("GET", "/internal/users");
    request.addHeader("X-Internal-Token", "secret");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void rejectsInternalRequestWithWrongToken() throws Exception {
    var filter = new InternalServiceTokenFilter("secret", "X-Internal-Token");
    var request = new MockHttpServletRequest("GET", "/internal/users");
    request.addHeader("X-Internal-Token", "wrong");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void failsClosedWhenTokenIsNotConfigured() throws Exception {
    var filter = new InternalServiceTokenFilter("", null);
    var request = new MockHttpServletRequest("GET", "/internal/users");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain, never()).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void ignoresNonInternalPaths() throws Exception {
    var filter = new InternalServiceTokenFilter("secret", null);
    var request = new MockHttpServletRequest("GET", "/api/v1/users/me");
    var response = new MockHttpServletResponse();

    filter.doFilter(request, response, chain);

    verify(chain).doFilter(request, response);
  }
}
