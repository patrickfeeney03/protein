package com.example.demo;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.Customizer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.example.demo.services.UserService;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.UUID;

@Configuration
@EnableWebSecurity
public class SecurityBeans {
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(
            @Value("${app.security.cookies.secure:false}") boolean secureCookies
    ) {
        var repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .secure(secureCookies)
                .sameSite("Lax")
                .path("/"));
        return repository;
    }

    /** Compatibility factory for focused unit tests and local callers. */
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return csrfTokenRepository(false);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins
    ) {
        var config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(Arrays.asList(
                "Content-Type", "X-XSRF-TOKEN", "Authorization", "Accept",
                "X-Requested-With", "Cache-Control"
        ));
        config.setExposedHeaders(Arrays.asList("Set-Cookie", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PersistentTokenRepository persistentTokenRepository(DataSource dataSource) {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("create table if not exists persistent_logins "
                    + "(username varchar(64) not null, series varchar(64) primary key, "
                    + "token varchar(64) not null, last_used timestamp not null)");
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize persistent remember-me token storage", e);
        }
        JdbcTokenRepositoryImpl repo = new JdbcTokenRepositoryImpl();
        repo.setDataSource(dataSource);
        repo.setCreateTableOnStartup(false);
        return repo;
    }

    @Bean
    public PersistentTokenBasedRememberMeServices rememberMeServices(
            UserDetailsService userDetailsService,
            PersistentTokenRepository persistentTokenRepository,
            @Value("${app.auth.remember-me-key:}") String rememberMeKey,
            @Value("${app.auth.require-remember-me-key:false}") boolean requireRememberMeKey,
            @Value("${app.security.cookies.secure:false}") boolean secureCookies
    ) {
        return createRememberMeServices(
                userDetailsService,
                persistentTokenRepository,
                rememberMeKey,
                requireRememberMeKey,
                secureCookies
        );
    }

    /** Compatibility factory for focused unit tests and local callers. */
    public PersistentTokenBasedRememberMeServices rememberMeServices(
            UserDetailsService userDetailsService,
            PersistentTokenRepository persistentTokenRepository,
            String rememberMeKey
    ) {
        return createRememberMeServices(userDetailsService, persistentTokenRepository, rememberMeKey, false, false);
    }

    private PersistentTokenBasedRememberMeServices createRememberMeServices(
            UserDetailsService userDetailsService,
            PersistentTokenRepository persistentTokenRepository,
            String rememberMeKey,
            boolean requireRememberMeKey,
            boolean secureCookies
    ) {
        if (requireRememberMeKey && !StringUtils.hasText(rememberMeKey)) {
            throw new IllegalStateException("app.auth.remember-me-key must be configured when production remember-me is enabled");
        }
        var key = StringUtils.hasText(rememberMeKey)
                ? rememberMeKey.trim()
                : UUID.randomUUID().toString();

        var services = new PersistentTokenBasedRememberMeServices(
                key,
                userDetailsService,
                persistentTokenRepository
        );

        services.setTokenValiditySeconds(7 * 24 * 60 * 60);
        services.setUseSecureCookie(secureCookies);
        services.setCookieCustomizer(cookie -> {
            cookie.setHttpOnly(true);
            cookie.setSecure(secureCookies);
            cookie.setAttribute("SameSite", "Lax");
            cookie.setPath("/");
        });

        return services;
    }

    @Bean
    public OriginVerificationFilter originVerificationFilter(
            @Value("${app.security.origin-verification.header-name:X-Origin-Shared-Secret}") String headerName,
            @Value("${app.security.origin-verification.shared-secret:}") String sharedSecret,
            @Value("${app.security.origin-verification.enabled:false}") boolean enabled,
            @Value("${app.security.origin-verification.enforce:false}") boolean enforce
    ) {
        if (enforce && (sharedSecret == null || sharedSecret.isBlank())) {
            throw new IllegalStateException("ORIGIN_SHARED_SECRET must be configured when origin verification is enforced");
        }
        return new OriginVerificationFilter(headerName, sharedSecret, enabled, enforce);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            RateLimitProperties rateLimitProperties,
            ObjectMapper objectMapper,
            CookieCsrfTokenRepository csrfTokenRepository,
            PersistentTokenBasedRememberMeServices rememberMeServices,
            UserService userService,
            OriginVerificationFilter originVerificationFilter,
            ClientRegistrationRepository clientRegistrationRepository,
            @Value("${app.auth.google.success-redirect:/}") String googleSuccessRedirect,
            @Value("${app.auth.google.failure-redirect:/login?error=oauth}") String googleFailureRedirect
    ) throws Exception {
        var googleLoginSuccessHandler = new GoogleLoginSuccessHandler(
                userService,
                rememberMeServices,
                googleSuccessRedirect,
                googleFailureRedirect
        );
        var authorizationRequestResolver = googleAuthorizationRequestResolver(clientRegistrationRepository);
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                )
                .cors(Customizer.withDefaults())
                .oauth2Login(oauth -> oauth
                        .authorizationEndpoint(ae -> ae
                                .baseUri("/api/auth")
                                .authorizationRequestResolver(authorizationRequestResolver))
                        .redirectionEndpoint(re -> re.baseUri("/api/auth/google/callback"))
                        .successHandler(googleLoginSuccessHandler)
                        .failureHandler(new GoogleLoginFailureHandler(googleFailureRedirect, googleSuccessRedirect))
                )
                .exceptionHandling(eh -> eh
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                new AntPathRequestMatcher("/api/**")
                        )
                )
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.FORWARD, DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/google", "/api/auth/google/callback").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/health/readiness").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/food", "/api/food/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/comment/**").permitAll()
                        .requestMatchers("/api", "/api/").hasRole("ADMIN")
                        .requestMatchers("/testing", "/testing/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/food/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )
                .rememberMe(rm -> rm
                        .rememberMeServices(rememberMeServices)
                )
                .headers(headers -> headers
                        .xssProtection(xss -> xss.disable())
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .cacheControl(Customizer.withDefaults())
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(originVerificationFilter, CsrfFilter.class)
                .addFilterBefore(
                        new SessionUserFilter(userService, rememberMeServices),
                        UsernamePasswordAuthenticationFilter.class
                )
                .addFilterAfter(
                        new RateLimitFilter(rateLimitProperties, objectMapper),
                        RememberMeAuthenticationFilter.class
                )
                .build();
    }

    /**
     * Resolves OAuth2 authorization requests under {@code /api/auth/{registrationId}} but lets
     * other {@code /api/auth/*} paths (me, csrf, logout) fall through instead of failing on an
     * unknown registration id.
     */
    private OAuth2AuthorizationRequestResolver googleAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository
    ) {
        var delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/api/auth");
        return new OAuth2AuthorizationRequestResolver() {
            @Override
            public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
                try {
                    return delegate.resolve(request);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }

            @Override
            public OAuth2AuthorizationRequest resolve(
                    HttpServletRequest request,
                    String clientRegistrationId
            ) {
                try {
                    return delegate.resolve(request, clientRegistrationId);
                } catch (IllegalArgumentException e) {
                    return null;
                }
            }
        };
    }
}
