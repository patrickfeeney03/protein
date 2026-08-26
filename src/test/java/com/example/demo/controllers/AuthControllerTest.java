package com.example.demo.controllers;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AuthControllerTest {

    @BeforeEach
    void clearSecurityContextBefore() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearSecurityContextAfter() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void csrfReturnsTokenDetails() {
        var controller = new AuthController(
                mock(UserService.class),
                mock(PersistentTokenBasedRememberMeServices.class)
        );

        CsrfToken csrfToken = mock(CsrfToken.class);
        when(csrfToken.getHeaderName()).thenReturn("X-CSRF-TOKEN");
        when(csrfToken.getToken()).thenReturn("token-value");

        var response = controller.csrf(csrfToken);

        assertThat(response.headerName()).isEqualTo("X-CSRF-TOKEN");
        assertThat(response.token()).isEqualTo("token-value");
    }

    @Test
    void logoutInvalidatesSessionAndClearsRememberMeState() {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var controller = new AuthController(userService, rememberMeServices);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        var authentication = new UsernamePasswordAuthenticationToken(
                "pat@example.com",
                null,
                java.util.List.of()
        );

        when(request.getSession(false)).thenReturn(session);

        var result = controller.logout(request, response, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        var order = inOrder(rememberMeServices, session);
        order.verify(rememberMeServices).logout(request, response, authentication);
        order.verify(session).removeAttribute("userId");
        order.verify(session).removeAttribute("email");
        order.verify(session).removeAttribute("authVersion");
        order.verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoMoreInteractions(rememberMeServices);
    }

    @Test
    void logoutUsesSessionEmailWhenAuthenticationIsAnonymous() {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var controller = new AuthController(userService, rememberMeServices);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        var anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("email")).thenReturn("pat@example.com");

        var result = controller.logout(request, response, anonymous);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        var order = inOrder(rememberMeServices, session);
        order.verify(rememberMeServices).logout(request, response, anonymous);
        order.verify(session).removeAttribute("userId");
        order.verify(session).removeAttribute("email");
        order.verify(session).removeAttribute("authVersion");
        order.verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void logoutUsesSessionUserIdWhenAnonymousSessionLacksEmail() {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var controller = new AuthController(userService, rememberMeServices);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        var anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );
        var user = new UserEntity();
        user.setEmail("pat@example.com");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(userService.get(7L)).thenReturn(java.util.Optional.of(user));

        var result = controller.logout(request, response, anonymous);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).get(7L);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        var order = inOrder(rememberMeServices, session);
        order.verify(rememberMeServices).logout(request, response, anonymous);
        order.verify(session).removeAttribute("userId");
        order.verify(session).removeAttribute("email");
        order.verify(session).removeAttribute("authVersion");
        order.verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void logoutContinuesWhenRememberMeRevocationFails() {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var controller = new AuthController(userService, rememberMeServices);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        var authentication = new UsernamePasswordAuthenticationToken(
                "pat@example.com",
                null,
                java.util.List.of()
        );

        when(request.getSession(false)).thenReturn(session);
        doThrow(new RuntimeException("db down"))
                .when(userService)
                .invalidateRememberMeTokens("pat@example.com");

        var result = controller.logout(request, response, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        var order = inOrder(rememberMeServices, session);
        order.verify(rememberMeServices).logout(request, response, authentication);
        order.verify(session).removeAttribute("userId");
        order.verify(session).removeAttribute("email");
        order.verify(session).removeAttribute("authVersion");
        order.verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoMoreInteractions(rememberMeServices);
    }

    @Test
    void logoutContinuesWhenSessionInvalidationFails() {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var controller = new AuthController(userService, rememberMeServices);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        var authentication = new UsernamePasswordAuthenticationToken(
                "pat@example.com",
                null,
                java.util.List.of()
        );

        when(request.getSession(false)).thenReturn(session);
        doThrow(new RuntimeException("session gone")).when(session).invalidate();

        var result = controller.logout(request, response, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        var order = inOrder(rememberMeServices, session);
        order.verify(rememberMeServices).logout(request, response, authentication);
        order.verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoMoreInteractions(rememberMeServices);
    }

    @Test
    void logoutStillInvalidatesSessionWhenClearingAuthStateFails() {
        var userService = mock(UserService.class);
        var rememberMeServices = mock(PersistentTokenBasedRememberMeServices.class);
        var controller = new AuthController(userService, rememberMeServices);

        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        HttpSession session = mock(HttpSession.class);
        var authentication = new UsernamePasswordAuthenticationToken(
                "pat@example.com",
                null,
                java.util.List.of()
        );

        when(request.getSession(false)).thenReturn(session);
        doThrow(new RuntimeException("stale session"))
                .when(session)
                .removeAttribute("userId");

        var result = controller.logout(request, response, authentication);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        verify(session).removeAttribute("userId");
        verify(session).invalidate();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void me_returnsUnauthorizedForAnonymousPrincipal() {
        var userService = mock(UserService.class);
        var controller = new AuthController(
                userService,
                mock(PersistentTokenBasedRememberMeServices.class)
        );

        var anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        );

        var response = controller.me(anonymous);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userService);
    }

    @Test
    void me_returnsUnauthorizedWhenPrincipalNoLongerMapsToUser() {
        var userService = mock(UserService.class);
        var controller = new AuthController(
                userService,
                mock(PersistentTokenBasedRememberMeServices.class)
        );

        var authentication = new UsernamePasswordAuthenticationToken(
                "deleted@example.com",
                null,
                java.util.List.of()
        );
        when(userService.getByEmail("deleted@example.com")).thenReturn(java.util.Optional.empty());

        var ex = assertThrows(ResponseStatusException.class, () -> controller.me(authentication));

        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(userService).getByEmail("deleted@example.com");
        verifyNoMoreInteractions(userService);
    }
}
