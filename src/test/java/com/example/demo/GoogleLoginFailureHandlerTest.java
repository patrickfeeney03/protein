package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import static org.assertj.core.api.Assertions.assertThat;

class GoogleLoginFailureHandlerTest {

    @Test
    void onAuthenticationFailure_redirectsToConfiguredFailureUrl() throws Exception {
        var handler = new GoogleLoginFailureHandler("/login?error=oauth", "/");
        var request = new MockHttpServletRequest("GET", "/api/auth/google/callback");
        request.setParameter("error", "access_denied");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(new OAuth2Error("access_denied"), "user cancelled")
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=oauth");
    }

    @Test
    void reusedCallbackAfterSuccessfulLogin_redirectsHome() throws Exception {
        var handler = new GoogleLoginFailureHandler("/login?error=oauth", "/");
        var request = new MockHttpServletRequest("GET", "/api/auth/google/callback");
        request.setParameter("code", "already-used");
        request.setParameter("state", "already-used");
        request.getSession(true).setAttribute("userId", 7L);
        request.getSession(true).setAttribute("email", "pat@example.com");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(
                        new OAuth2Error("authorization_request_not_found"),
                        "authorization_request_not_found"
                )
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("/");
    }

    @Test
    void missingAuthorizationRequestWithoutSession_stillFails() throws Exception {
        var handler = new GoogleLoginFailureHandler("/login?error=oauth", "/");
        var request = new MockHttpServletRequest("GET", "/api/auth/google/callback");
        request.setParameter("code", "orphan");
        request.setParameter("state", "orphan");
        var response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(
                request,
                response,
                new OAuth2AuthenticationException(
                        new OAuth2Error("authorization_request_not_found"),
                        "authorization_request_not_found"
                )
        );

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=oauth");
    }
}
