package com.example.demo;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter();

    @AfterEach
    void clearSecurityAndMdc() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void successfulApiRequest_isLoggedAtInfoWithUserAndRequestId() throws Exception {
        var appender = attachAppender();
        var request = new MockHttpServletRequest("GET", "/api/food");
        var response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("Pat@Example.com", null, AuthorityUtils.createAuthorityList("ROLE_USER"))
        );

        try {
            filter.doFilter(request, response, statusChain(200));
        } finally {
            detachAppender(appender);
        }

        var event = lastEvent(appender);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getFormattedMessage())
                .contains("HTTP GET /api/food")
                .contains("status=200")
                .contains("user=pat@example.com")
                .contains("queryKeys=-");
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
        assertThat(MDC.get(RequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
    }

    @Test
    void incomingRequestId_isReusedWhenSafe() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/food");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "req-123");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChain(200));

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("req-123");
    }

    @Test
    void incomingRequestId_isReplacedWhenUnsafe() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/food");
        request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "not a valid id\n");
        var response = new MockHttpServletResponse();

        filter.doFilter(request, response, statusChain(200));

        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotEqualTo("not a valid id\n");
        assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNotBlank();
    }

    @Test
    void clientError_isLoggedAtWarnWithoutQueryValues() throws Exception {
        var appender = attachAppender();
        var request = new MockHttpServletRequest("GET", "/api/auth/google/callback");
        request.setParameter("code", "secret-oauth-code");
        request.setParameter("state", "abc");
        var response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, statusChain(401));
        } finally {
            detachAppender(appender);
        }

        var event = lastEvent(appender);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getFormattedMessage())
                .contains("status=401")
                .contains("queryKeys=code,state")
                .doesNotContain("secret-oauth-code");
    }

    @Test
    void serverError_isLoggedAtError() throws Exception {
        var appender = attachAppender();
        var request = new MockHttpServletRequest("POST", "/api/food");
        var response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, statusChain(500));
        } finally {
            detachAppender(appender);
        }

        assertThat(lastEvent(appender).getLevel()).isEqualTo(Level.ERROR);
        assertThat(lastEvent(appender).getFormattedMessage()).contains("status=500");
    }

    @Test
    void healthCheck_isNotLoggedAtInfo() throws Exception {
        var appender = attachAppender();
        var request = new MockHttpServletRequest("GET", "/api/health");
        var response = new MockHttpServletResponse();

        try {
            filter.doFilter(request, response, statusChain(200));
        } finally {
            detachAppender(appender);
        }

        assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.INFO));
    }

    @Test
    void anonymousUser_isLoggedAsDash() throws Exception {
        var appender = attachAppender();
        var request = new MockHttpServletRequest("GET", "/api/food");
        var response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
        );

        try {
            filter.doFilter(request, response, statusChain(200));
        } finally {
            detachAppender(appender);
        }

        assertThat(lastEvent(appender).getFormattedMessage()).contains("user=-");
    }

    @Test
    void filterException_isLoggedAndRethrown() {
        var appender = attachAppender();
        var request = new MockHttpServletRequest("GET", "/api/food");
        var response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            throw new ServletException("boom");
        };

        try {
            assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                    .isInstanceOf(ServletException.class)
                    .hasMessage("boom");
            assertThat(lastEvent(appender).getLevel()).isEqualTo(Level.ERROR);
            assertThat(lastEvent(appender).getFormattedMessage()).contains("failed");
        } finally {
            detachAppender(appender);
        }
    }

    private FilterChain statusChain(int status) {
        return (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
    }

    private ListAppender<ILoggingEvent> attachAppender() {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        logger.setLevel(Level.INFO);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachAppender(ListAppender<ILoggingEvent> appender) {
        var logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        logger.detachAppender(appender);
        appender.stop();
    }

    private ILoggingEvent lastEvent(ListAppender<ILoggingEvent> appender) {
        assertThat(appender.list).isNotEmpty();
        return appender.list.getLast();
    }
}
