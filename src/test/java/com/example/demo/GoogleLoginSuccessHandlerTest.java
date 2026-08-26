package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.GoogleIdentity;
import com.example.demo.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleLoginSuccessHandlerTest {

    @Test
    void onAuthenticationSuccess_setsSessionRotatesIdAndIssuesRememberMe() throws Exception {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var handler = new GoogleLoginSuccessHandler(userService, rememberMeServices, "/", "/login?error=oauth");

        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setName("Pat");
        user.setAuthVersion(3);
        user.setAdmin(true);
        when(userService.findOrCreateGoogleUser(any(GoogleIdentity.class))).thenReturn(user);

        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);

        handler.onAuthenticationSuccess(request, response, authenticationWith(googlePrincipal("sub-1", "pat@example.com", "Pat", true)));

        verify(request).changeSessionId();
        verify(session).setAttribute("userId", 7L);
        verify(session).setAttribute("email", "pat@example.com");
        verify(session).setAttribute("authVersion", 3);
        verify(rememberMeServices).loginSuccess(eq(request), eq(response), authWithAuthorities("ROLE_USER", "ROLE_ADMIN"));
        verify(response).setStatus(HttpServletResponse.SC_FOUND);
        verify(response).setHeader("Location", "/");
    }

    @Test
    void onAuthenticationSuccess_nonAdminIssuesUserRoleOnly() throws Exception {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var handler = new GoogleLoginSuccessHandler(userService, rememberMeServices, "/", "/login?error=oauth");

        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setName("Pat");
        user.setAuthVersion(3);
        user.setAdmin(false);
        when(userService.findOrCreateGoogleUser(any(GoogleIdentity.class))).thenReturn(user);

        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);
        var session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);

        handler.onAuthenticationSuccess(request, response, authenticationWith(googlePrincipal("sub-1", "pat@example.com", "Pat", true)));

        verify(rememberMeServices).loginSuccess(eq(request), eq(response), authWithAuthorities("ROLE_USER"));
        verify(response).setStatus(HttpServletResponse.SC_FOUND);
        verify(response).setHeader("Location", "/");
    }

    @Test
    void onAuthenticationSuccess_unverifiedEmailRedirectsToFailureAndDoesNotMutateSession() throws Exception {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var handler = new GoogleLoginSuccessHandler(userService, rememberMeServices, "/", "/login?error=oauth");

        when(userService.findOrCreateGoogleUser(any(GoogleIdentity.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Google email not verified"));

        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        handler.onAuthenticationSuccess(request, response, authenticationWith(googlePrincipal("sub-1", "pat@example.com", "Pat", false)));

        verify(response).setStatus(HttpServletResponse.SC_FOUND);
        verify(response).setHeader("Location", "/login?error=oauth");
        verify(request, never()).getSession();
        verify(rememberMeServices, never()).loginSuccess(any(), any(), any());
    }

    @Test
    void onAuthenticationSuccess_serviceFailureRedirectsToFailure() throws Exception {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var handler = new GoogleLoginSuccessHandler(userService, rememberMeServices, "/", "/login?error=oauth");

        when(userService.findOrCreateGoogleUser(any(GoogleIdentity.class)))
                .thenThrow(new IllegalStateException("db down"));

        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        handler.onAuthenticationSuccess(request, response, authenticationWith(googlePrincipal("sub-1", "pat@example.com", "Pat", true)));

        verify(response).setStatus(HttpServletResponse.SC_FOUND);
        verify(response).setHeader("Location", "/login?error=oauth");
        verify(request, never()).getSession();
        verify(rememberMeServices, never()).loginSuccess(any(), any(), any());
    }

    @Test
    void onAuthenticationSuccess_missingPrincipalRedirectsToFailure() throws Exception {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var handler = new GoogleLoginSuccessHandler(userService, rememberMeServices, "/", "/login?error=oauth");

        var request = mock(HttpServletRequest.class);
        var response = mock(HttpServletResponse.class);

        handler.onAuthenticationSuccess(request, response, new UsernamePasswordAuthenticationToken("not-an-oauth-user", null));

        verify(response).setStatus(HttpServletResponse.SC_FOUND);
        verify(response).setHeader("Location", "/login?error=oauth");
        verify(request, never()).getSession();
        verify(rememberMeServices, never()).loginSuccess(any(), any(), any());
    }

    private Authentication authenticationWith(OAuth2User principal) {
        return new UsernamePasswordAuthenticationToken(principal, null);
    }

    private OAuth2User googlePrincipal(String sub, String email, String name, boolean emailVerified) {
        var principal = mock(OAuth2User.class);
        when(principal.getAttribute("sub")).thenReturn(sub);
        when(principal.getAttribute("email")).thenReturn(email);
        when(principal.getAttribute("name")).thenReturn(name);
        when(principal.getAttribute("email_verified")).thenReturn(emailVerified);
        return principal;
    }

    private Authentication authWithAuthorities(String... authorities) {
        return org.mockito.ArgumentMatchers.argThat(auth -> {
            var actual = auth.getAuthorities().stream().map(Object::toString).toList();
            return actual.equals(java.util.List.of(authorities));
        });
    }
}
