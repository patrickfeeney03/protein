package com.example.demo;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Sends a 302 with the Location header left exactly as configured.
 * {@link HttpServletResponse#sendRedirect(String)} rewrites relative paths
 * against the servlet request origin, which is loopback behind nginx and
 * would be stripped by the Pages /api proxy.
 */
final class BrowserRedirect {
    private BrowserRedirect() {
    }

    static void send(HttpServletResponse response, String location) {
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", location);
    }
}
