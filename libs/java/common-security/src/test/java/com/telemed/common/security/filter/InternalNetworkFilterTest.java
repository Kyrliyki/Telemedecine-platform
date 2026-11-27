package com.telemed.common.security.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class InternalNetworkFilterTest {

  private final InternalNetworkFilter filter =
      new InternalNetworkFilter(List.of("127.0.0.1/32", "10.0.0.0/8"));

  @Test
  void allowsConfiguredNetwork() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("10.1.2.3");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNotNull();
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void rejectsAddressOutsideConfiguredNetworks() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("192.168.1.10");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void emptyAllowListRejectsEverything() throws Exception {
    InternalNetworkFilter denyAll = new InternalNetworkFilter(List.of());
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    denyAll.doFilter(request, response, chain);

    assertThat(chain.getRequest()).isNull();
    assertThat(response.getStatus()).isEqualTo(403);
  }
}
