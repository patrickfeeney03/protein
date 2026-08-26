package com.example.demo;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OriginVerificationFilterTest {
    @Test
    void enforcedFilterRequiresSharedSecretForApiRequests() throws Exception {
        var filter = new OriginVerificationFilter("X-Origin-Shared-Secret", "secret", true, true);
        var request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        request.setServletPath("/api/auth/csrf");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, org.mockito.Mockito.never()).doFilter(request, response);
    }

    @Test
    void validSecretMarksRequestAndHealthIsExempt() throws Exception {
        var filter = new OriginVerificationFilter("X-Origin-Shared-Secret", "secret", true, true);
        var request = new MockHttpServletRequest("POST", "/api/auth/logout");
        request.setServletPath("/api/auth/logout");
        request.addHeader("X-Origin-Shared-Secret", "secret");
        var response = new MockHttpServletResponse();
        var chain = mock(FilterChain.class);
        filter.doFilter(request, response, chain);
        assertThat(request.getAttribute(OriginVerificationFilter.VERIFIED_ATTRIBUTE)).isEqualTo(true);
        verify(chain).doFilter(request, response);

        var healthRequest = new MockHttpServletRequest("GET", "/api/health");
        healthRequest.setServletPath("/api/health");
        var healthResponse = new MockHttpServletResponse();
        filter.doFilter(healthRequest, healthResponse, chain);
        verify(chain).doFilter(healthRequest, healthResponse);

        var apiRootRequest = new MockHttpServletRequest("GET", "/api");
        apiRootRequest.setServletPath("/api");
        var apiRootResponse = new MockHttpServletResponse();
        filter.doFilter(apiRootRequest, apiRootResponse, chain);
        assertThat(apiRootResponse.getStatus()).isEqualTo(403);
    }
}
