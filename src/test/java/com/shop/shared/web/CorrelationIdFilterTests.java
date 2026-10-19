package com.shop.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTests {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void keepsAValidIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "checkout-123");

        filter.doFilter(
                request, response, (servletRequest, servletResponse) -> assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                        .isEqualTo("checkout-123"));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo("checkout-123");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void replacesAnInvalidCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "invalid correlation id");

        filter.doFilter(
                request, response, (servletRequest, servletResponse) -> assertThat(MDC.get(CorrelationIdFilter.MDC_KEY))
                        .isNotBlank()
                        .isNotEqualTo("invalid correlation id"));

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME))
                .isNotBlank()
                .isNotEqualTo("invalid correlation id");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
