package com.example.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;

/**
 * Optional edge-to-origin shared-secret guard for API requests. It can run in
 * report-only mode during rollout and be enforced in production. CORS/CSRF
 * remain the browser-origin and browser-token controls respectively.
 */
public class OriginVerificationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(OriginVerificationFilter.class);
    private static final String DEFAULT_HEADER_NAME = "X-Origin-Shared-Secret";
    public static final String VERIFIED_ATTRIBUTE = OriginVerificationFilter.class.getName() + ".VERIFIED";

    private final boolean enabled;
    private final boolean enforce;
    private final String headerName;
    private final String expectedSecret;

    public OriginVerificationFilter(String headerName, String expectedSecret, boolean enabled, boolean enforce) {
        this.enabled = enabled;
        this.enforce = enforce;
        this.headerName = headerName == null || headerName.isBlank() ? DEFAULT_HEADER_NAME : headerName.trim();
        this.expectedSecret = expectedSecret == null ? "" : expectedSecret;
    }

    public OriginVerificationFilter(String expectedSecret, boolean enabled, boolean enforce) {
        this(DEFAULT_HEADER_NAME, expectedSecret, enabled, enforce);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var path = normalizePath(request.getRequestURI());
        if (!enabled || !("/api".equals(path) || path.startsWith("/api/"))
                || "/api/health".equals(path) || "/api/health/readiness".equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        var suppliedSecret = request.getHeader(headerName);
        if (hasValidSecret(suppliedSecret)) {
            request.setAttribute(VERIFIED_ATTRIBUTE, Boolean.TRUE);
            filterChain.doFilter(request, response);
            return;
        }

        if (!enforce) {
            LOGGER.warn("Origin-shared-secret report-only event method={} path={}", request.getMethod(), path);
            filterChain.doFilter(request, response);
            return;
        }

        LOGGER.warn("Origin-shared-secret rejected method={} path={}", request.getMethod(), path);
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Request origin verification failed");
    }

    boolean hasValidSecret(String suppliedSecret) {
        if (expectedSecret.isBlank() || suppliedSecret == null || suppliedSecret.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expectedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                suppliedSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }
}
