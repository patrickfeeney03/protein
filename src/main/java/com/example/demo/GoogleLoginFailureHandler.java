package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;

public class GoogleLoginFailureHandler implements AuthenticationFailureHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleLoginFailureHandler.class);
    private static final String AUTHORIZATION_REQUEST_NOT_FOUND = "authorization_request_not_found";

    private final String failureRedirect;
    private final String successRedirect;

    public GoogleLoginFailureHandler(String failureRedirect) {
        this(failureRedirect, "/");
    }

    public GoogleLoginFailureHandler(String failureRedirect, String successRedirect) {
        this.failureRedirect = failureRedirect;
        this.successRedirect = successRedirect;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        if (isConsumedAuthorizationRequest(exception) && hasEstablishedSession(request)) {
            LOGGER.info("Google callback reused after a completed login; redirecting home");
            BrowserRedirect.send(response, successRedirect);
            return;
        }

        LOGGER.warn(
                "Google login failed: path={} hasSession={} hasCode={} hasState={} googleError={} googleErrorDescription={} exception={}",
                request.getRequestURI(),
                request.getSession(false) != null,
                request.getParameter("code") != null,
                request.getParameter("state") != null,
                request.getParameter("error"),
                request.getParameter("error_description"),
                exception == null ? "null" : exception.toString(),
                exception
        );
        BrowserRedirect.send(response, failureRedirect);
    }

    private boolean isConsumedAuthorizationRequest(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauth
                && AUTHORIZATION_REQUEST_NOT_FOUND.equals(oauth.getError().getErrorCode());
    }

    private boolean hasEstablishedSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return false;
        }
        Object userId = session.getAttribute("userId");
        Object email = session.getAttribute("email");
        return userId instanceof Long || (email instanceof String value && !value.isBlank());
    }
}
