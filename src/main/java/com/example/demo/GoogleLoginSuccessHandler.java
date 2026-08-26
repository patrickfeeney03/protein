package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.GoogleIdentity;
import com.example.demo.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;

import java.io.IOException;
import java.util.List;

public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GoogleLoginSuccessHandler.class);

    private final UserService userService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;
    private final String successRedirect;
    private final String failureRedirect;

    public GoogleLoginSuccessHandler(
            UserService userService,
            PersistentTokenBasedRememberMeServices rememberMeServices,
            String successRedirect,
            String failureRedirect
    ) {
        this.userService = userService;
        this.rememberMeServices = rememberMeServices;
        this.successRedirect = successRedirect;
        this.failureRedirect = failureRedirect;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException {
        OAuth2User principal = resolvePrincipal(authentication);
        if (principal == null) {
            LOGGER.warn("Google login succeeded at the provider but the principal was not an OAuth2User");
            BrowserRedirect.send(response, failureRedirect);
            return;
        }

        String sub = principal.getAttribute("sub");
        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        Boolean emailVerified = principal.getAttribute("email_verified");
        boolean verified = Boolean.TRUE.equals(emailVerified);

        UserEntity user;
        try {
            user = userService.findOrCreateGoogleUser(new GoogleIdentity(sub, email, name, verified));
        } catch (RuntimeException e) {
            LOGGER.warn("Google login failed to find-or-create local user", e);
            BrowserRedirect.send(response, failureRedirect);
            return;
        }

        SecurityContextHolder.clearContext();
        rememberMeServices.logout(request, response, null);
        request.changeSessionId();

        var session = request.getSession();
        session.setAttribute("userId", user.getId());
        session.setAttribute("email", user.getEmail());
        session.setAttribute("authVersion", user.getAuthVersion());

        var auth = new UsernamePasswordAuthenticationToken(
                user.getEmail(),
                null,
                authoritiesFor(user)
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        rememberMeServices.loginSuccess(request, response, auth);
        BrowserRedirect.send(response, successRedirect);
    }

    private OAuth2User resolvePrincipal(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof OAuth2User oauth2User ? oauth2User : null;
    }

    private static List<GrantedAuthority> authoritiesFor(UserEntity user) {
        if (user != null && user.isAdmin()) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_ADMIN")
            );
        }

        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
