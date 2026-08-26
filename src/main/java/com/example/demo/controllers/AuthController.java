package com.example.demo.controllers;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    public record CsrfResponse(String headerName, String token) {}

    private final UserService userService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;

    public AuthController(
            UserService userService,
            PersistentTokenBasedRememberMeServices rememberMeServices
    ) {
        this.userService = userService;
        this.rememberMeServices = rememberMeServices;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken csrfToken) {
        if (csrfToken == null) {
            return new CsrfResponse("X-XSRF-TOKEN", "");
        }
        return new CsrfResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) {
        HttpSession session = request.getSession(false);
        String email = resolveLogoutEmail(authentication, session);

        try {
            if (email != null) {
                try {
                    userService.invalidateRememberMeTokens(email);
                } catch (RuntimeException e) {
                    LOGGER.warn("Failed to revoke remember-me tokens during logout", e);
                }
            }
            try {
                rememberMeServices.logout(request, response, authentication);
            } catch (RuntimeException e) {
                LOGGER.warn("Failed to clear remember-me state during logout", e);
            }
            if (session != null) {
                try {
                    clearSessionAuthState(session);
                } catch (RuntimeException e) {
                    LOGGER.warn("Failed to clear session auth state during logout", e);
                }
                try {
                    session.invalidate();
                } catch (RuntimeException e) {
                    LOGGER.warn("Failed to invalidate session during logout", e);
                }
            }
        } finally {
            SecurityContextHolder.clearContext();
        }

        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserEntity.MeResponse> me(Authentication authentication) {
        if (isAnonymous(authentication)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        var user = userService.getByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        return ResponseEntity.ok(userService.me(user.getId()));
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    private String resolveLogoutEmail(Authentication authentication, HttpSession session) {
        if (!isAnonymous(authentication) && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }

        if (session == null) {
            return null;
        }

        Object email = session.getAttribute("email");
        if (email instanceof String value && !value.isBlank()) {
            return value;
        }

        Object userId = session.getAttribute("userId");
        if (userId instanceof Long id) {
            return userService.get(id)
                    .map(UserEntity::getEmail)
                    .filter(value -> value != null && !value.isBlank())
                    .orElse(null);
        }

        return null;
    }

    private void clearSessionAuthState(HttpSession session) {
        session.removeAttribute("userId");
        session.removeAttribute("email");
        session.removeAttribute("authVersion");
    }
}
