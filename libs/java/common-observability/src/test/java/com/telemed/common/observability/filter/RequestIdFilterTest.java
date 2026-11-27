package com.telemed.common.observability.filter;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void reusesIncomingRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-42");
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesRequestIdWhenMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
