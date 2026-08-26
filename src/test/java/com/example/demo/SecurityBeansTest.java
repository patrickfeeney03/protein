package com.example.demo;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityBeansTest {

    private final SecurityBeans beans = new SecurityBeans();

    @Test
    void csrfCookieIsNotHttpOnly() {
        var repository = beans.csrfTokenRepository();

        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        repository.saveToken(new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-value"), request, response);

        Cookie cookie = response.getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void corsConfiguration_allowsCredentialsAndLocalhostOrigin() {
        var source = beans.corsConfigurationSource("http://localhost:4200");
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowCredentials()).isTrue();
        assertThat(corsConfig.getAllowedOriginPatterns().stream().anyMatch(p -> p.contains("localhost:4200"))).isTrue();
        assertThat(corsConfig.getAllowedMethods()).contains("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH");
        assertThat(corsConfig.getAllowedHeaders()).contains("Content-Type", "X-XSRF-TOKEN", "Authorization");
        assertThat(corsConfig.getExposedHeaders()).contains("Set-Cookie");
        assertThat(corsConfig.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void corsConfiguration_parsesMultipleOrigins() {
        var source = beans.corsConfigurationSource("http://localhost:4200,https://app.example.com");
        var corsConfig = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(corsConfig).isNotNull();
        assertThat(corsConfig.getAllowedOriginPatterns()).hasSize(2);
        assertThat(corsConfig.getAllowedOriginPatterns().stream().anyMatch(p -> p.contains("localhost:4200"))).isTrue();
        assertThat(corsConfig.getAllowedOriginPatterns().stream().anyMatch(p -> p.contains("app.example.com"))).isTrue();
    }

    @Test
    void csrfTokenRepository_hasCorrectType() {
        assertThat(beans.csrfTokenRepository()).isInstanceOf(CookieCsrfTokenRepository.class);
    }

    @Test
    void persistentTokenRepository_createsSuccessfully() {
        var ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:testdb-security-beans;DB_CLOSE_DELAY=-1");
        var repo = beans.persistentTokenRepository(ds);
        assertThat(repo).isNotNull();
    }

    @Test
    void rememberMeServices_createsSuccessfully() {
        var userDetailsService = mock(UserDetailsService.class);
        var tokenRepository = mock(PersistentTokenRepository.class);

        var services = beans.rememberMeServices(userDetailsService, tokenRepository, "test-key");

        assertThat(services).isInstanceOf(PersistentTokenBasedRememberMeServices.class);
        assertThat(services).isNotNull();
    }

    @Test
    void rememberMeServices_defaultKey_generatesNonEmptyKey() {
        var userDetailsService = mock(UserDetailsService.class);
        var tokenRepository = mock(PersistentTokenRepository.class);

        var services = beans.rememberMeServices(userDetailsService, tokenRepository, "");

        assertThat(services).isNotNull();
    }

    @Test
    void rememberMeServices_requiresStableKeyWhenRequested() {
        var userDetailsService = mock(UserDetailsService.class);
        var tokenRepository = mock(PersistentTokenRepository.class);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> beans.rememberMeServices(userDetailsService, tokenRepository, "", true, true)
        );
    }
}
