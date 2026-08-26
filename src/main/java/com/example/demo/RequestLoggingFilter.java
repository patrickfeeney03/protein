package com.example.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Access-style request log plus a request id on every log line for the same call.
 * Query values, cookies, and headers are not logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_HEADER = "X-Request-ID";
    static final String REQUEST_ID_MDC_KEY = "requestId";

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
        var method = request.getMethod();
        var path = requestPath(request);
        var startedAt = System.nanoTime();

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            LOGGER.debug("HTTP {} {} started", method, path);
            filterChain.doFilter(request, response);
            logCompleted(method, path, request, response.getStatus(), elapsedMillis(startedAt), null);
        } catch (ServletException | IOException | RuntimeException ex) {
            logCompleted(method, path, request, response.getStatus(), elapsedMillis(startedAt), ex);
            throw ex;
        } finally {
            MDC.remove(REQUEST_ID_MDC_KEY);
        }
    }

    private void logCompleted(
            String method,
            String path,
            HttpServletRequest request,
            int status,
            long durationMs,
            Exception failure
    ) {
        var user = currentUser();
        var queryKeys = queryKeys(request);
        if (failure != null) {
            LOGGER.error(
                    "HTTP {} {} status={} durationMs={} user={} queryKeys={} failed",
                    method,
                    path,
                    status == 0 ? 500 : status,
                    durationMs,
                    user,
                    queryKeys,
                    failure
            );
            return;
        }

        var message = "HTTP {} {} status={} durationMs={} user={} queryKeys={}";
        if (status >= 500) {
            LOGGER.error(message, method, path, status, durationMs, user, queryKeys);
        } else if (status >= 400) {
            LOGGER.warn(message, method, path, status, durationMs, user, queryKeys);
        } else if (isQuietPath(method, path)) {
            LOGGER.debug(message, method, path, status, durationMs, user, queryKeys);
        } else {
            LOGGER.info(message, method, path, status, durationMs, user, queryKeys);
        }
    }

    static String resolveRequestId(String incoming) {
        if (incoming != null && SAFE_REQUEST_ID.matcher(incoming.trim()).matches()) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }

    static boolean isQuietPath(String method, String path) {
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        return "/api/health".equals(path) || "/api/health/readiness".equals(path);
    }

    static String requestPath(HttpServletRequest request) {
        var uri = request.getRequestURI();
        if (uri == null || uri.isBlank()) {
            return "/";
        }
        var context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri.length() > 1 && uri.endsWith("/")) {
            uri = uri.substring(0, uri.length() - 1);
        }
        return uri.isEmpty() ? "/" : uri;
    }

    static String queryKeys(HttpServletRequest request) {
        var names = request.getParameterNames();
        if (names == null || !names.hasMoreElements()) {
            return "-";
        }
        var keys = new ArrayList<String>();
        while (names.hasMoreElements()) {
            keys.add(names.nextElement());
        }
        Collections.sort(keys);
        return String.join(",", keys);
    }

    static String currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return "-";
        }
        var name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
            return "-";
        }
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private static long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
