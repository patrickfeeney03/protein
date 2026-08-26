package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class SessionUserFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionUserFilter.class);

    private final UserService userService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;

    public SessionUserFilter(UserService userService, PersistentTokenBasedRememberMeServices rememberMeServices) {
        this.userService = userService;
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        var context = SecurityContextHolder.getContext();

        HttpSession session = request.getSession(false);
        if (session != null) {
            Long userId = (Long) session.getAttribute("userId");
            String email = (String) session.getAttribute("email");
            Integer authVersion = (Integer) session.getAttribute("authVersion");
            boolean hasAnyAuthState = userId != null || email != null || authVersion != null;
            boolean hasCompleteAuthState = userId != null && email != null && authVersion != null;

            if (hasAnyAuthState && !hasCompleteAuthState) {
                String emailToInvalidate = resolveEmailForInvalidation(userId, email);
                if (emailToInvalidate != null) {
                    try {
                        userService.invalidateRememberMeTokens(emailToInvalidate);
                    } catch (RuntimeException e) {
                        LOGGER.warn("Failed to revoke remember-me tokens for stale session userId={}", userId, e);
                    }
                }
                clearRememberMeState(request, response);
                clearSessionAuthStateAndInvalidate(session);
                SecurityContextHolder.clearContext();
            } else if (hasCompleteAuthState) {
                var user = userService.get(userId).orElse(null);
                if (user != null && email.equals(user.getEmail()) && authVersion == user.getAuthVersion()) {
                    var auth =
                            new UsernamePasswordAuthenticationToken(
                                    email,
                                    null,
                                    authoritiesFor(user)
                            );
                    auth.setDetails(userId);
                    context.setAuthentication(auth);
                } else {
                    try {
                        userService.invalidateRememberMeTokens(email);
                    } catch (RuntimeException e) {
                        LOGGER.warn("Failed to revoke remember-me tokens for stale session userId={}", userId, e);
                    }
                    clearRememberMeState(request, response);
                    clearSessionAuthStateAndInvalidate(session);
                    SecurityContextHolder.clearContext();
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveEmailForInvalidation(Long userId, String email) {
        if (email != null && !email.isBlank()) {
            return email;
        }

        if (userId == null) {
            return null;
        }

        return userService.get(userId)
                .map(UserEntity::getEmail)
                .filter(value -> value != null && !value.isBlank())
                .orElse(null);
    }

    private void clearRememberMeState(HttpServletRequest request, HttpServletResponse response) {
        try {
            // Clear the remember-me cookie without relying on the current thread auth.
            rememberMeServices.logout(request, response, null);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to clear remember-me cookie during stale session cleanup", e);
        }
    }

    private void clearSessionAuthState(HttpSession session) {
        session.removeAttribute("userId");
        session.removeAttribute("email");
        session.removeAttribute("authVersion");
    }

    private void clearSessionAuthStateAndInvalidate(HttpSession session) {
        try {
            clearSessionAuthState(session);
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to clear session auth state during stale session cleanup", e);
        }

        try {
            session.invalidate();
        } catch (RuntimeException e) {
            LOGGER.warn("Failed to invalidate session during stale session cleanup", e);
        }
    }

    private List<GrantedAuthority> authoritiesFor(UserEntity user) {
        if (user != null && user.isAdmin()) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }

        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
