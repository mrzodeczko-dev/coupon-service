package com.rzodeczko.infrastructure.logging;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void shouldGenerateRequestIdWhenHeaderMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String issued = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(issued).isNotBlank();
        // proves it's a real UUID
        assertThat(UUID.fromString(issued)).isNotNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldReuseIncomingRequestId() throws Exception {
        String incoming = "req-12345";
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, incoming);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationIdFilter.HEADER_NAME)).isEqualTo(incoming);
    }

    @Test
    void shouldPutRequestIdInMdcDuringChainAndClearAfter() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "req-in-chain");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        doAnswer(inv -> {
            mdcDuringChain.set(MDC.get(CorrelationIdFilter.MDC_KEY));
            return null;
        }).when(chain).doFilter(any(), any());

        filter.doFilter(request, response, chain);

        assertThat(mdcDuringChain.get()).isEqualTo("req-in-chain");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void shouldGenerateNewIdWhenIncomingHeaderIsBlank() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER_NAME, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String issued = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(issued).isNotBlank();
        assertThat(UUID.fromString(issued)).isNotNull();
    }
}
