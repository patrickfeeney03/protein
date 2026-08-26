package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionUserFilterTest {
    @Mock
    private UserService userService;

    @Mock
    private PersistentTokenBasedRememberMeServices rememberMeServices;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private HttpSession session;

    @BeforeEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void resetSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void restoresAuthenticationWhenSessionStillMatchesUser() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setAuthVersion(3);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("pat@example.com", authentication.getName());
        verify(session, never()).invalidate();
        verify(rememberMeServices, never()).logout(request, response, null);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsStaleSessionWhenUserNoLongerMatches() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.empty());

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService).get(7L);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        verify(rememberMeServices).logout(request, response, null);
        verify(session).removeAttribute("userId");
        verify(session).removeAttribute("email");
        verify(session).removeAttribute("authVersion");
        verify(session).invalidate();
        verify(filterChain).doFilter(request, response);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void rejectsSessionWhenAuthVersionChanges() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setAuthVersion(4);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("db down")).when(userService).invalidateRememberMeTokens("pat@example.com");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        verify(rememberMeServices).logout(request, response, null);
        verify(session).removeAttribute("userId");
        verify(session).removeAttribute("email");
        verify(session).removeAttribute("authVersion");
        verify(session).invalidate();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsPartiallyPopulatedAuthSession() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        verify(rememberMeServices).logout(request, response, null);
        verify(session).invalidate();
        verify(filterChain).doFilter(request, response);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void invalidatesStaleSessionEvenIfClearingAttributesFails() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.empty());
        doThrow(new RuntimeException("attribute removal failed")).when(session).removeAttribute("userId");

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        verify(rememberMeServices).logout(request, response, null);
        verify(session).removeAttribute("userId");
        verify(session).invalidate();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void resolvesEmailFromUserRecordWhenPartialSessionLacksEmail() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn(null);
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userService).get(7L);
        verify(userService).invalidateRememberMeTokens("pat@example.com");
        verify(rememberMeServices).logout(request, response, null);
        verify(session).invalidate();
        verify(filterChain).doFilter(request, response);
        verifyNoMoreInteractions(userService);
    }

    @Test
    void rejectsStaleAuthenticatedSessionWhenAuthVersionChanges() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setAuthVersion(4);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.of(user));

        var authentication = new UsernamePasswordAuthenticationToken(
                "pat@example.com",
                "n/a",
                java.util.List.of()
        );
        SecurityContextHolder.getContext().setAuthentication(
                authentication
        );

        filter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(rememberMeServices).logout(request, response, null);
        verify(session).removeAttribute("userId");
        verify(session).removeAttribute("email");
        verify(session).removeAttribute("authVersion");
        verify(session).invalidate();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void replacesAuthenticatedContextWhenSessionBelongsToDifferentUser() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setAuthVersion(3);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.of(user));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("other@example.com", "n/a", java.util.List.of())
        );

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("pat@example.com", authentication.getName());
        verify(session, never()).invalidate();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void refreshesAuthoritiesWhenMatchingSessionAlreadyHasStaleAuthentication() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setAuthVersion(3);
        user.setAdmin(true);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.of(user));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("pat@example.com", "n/a", java.util.List.of())
        );

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals(
                java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                ),
                authentication.getAuthorities().stream().toList()
        );
        verify(session, never()).invalidate();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void restoresAdminAuthorityForAdminSessions() throws Exception {
        var filter = new SessionUserFilter(userService, rememberMeServices);
        var user = new UserEntity();
        user.setId(7L);
        user.setEmail("pat@example.com");
        user.setAuthVersion(3);
        user.setAdmin(true);

        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("userId")).thenReturn(7L);
        when(session.getAttribute("email")).thenReturn("pat@example.com");
        when(session.getAttribute("authVersion")).thenReturn(3);
        when(userService.get(7L)).thenReturn(Optional.of(user));

        filter.doFilter(request, response, filterChain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("pat@example.com", authentication.getName());
        assertEquals(
                java.util.List.of(
                        new SimpleGrantedAuthority("ROLE_USER"),
                        new SimpleGrantedAuthority("ROLE_ADMIN")
                ),
                authentication.getAuthorities().stream().toList()
        );
        verify(session, never()).invalidate();
        verify(filterChain).doFilter(request, response);
    }
}
