package com.example.demo;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CsrfSecurityIntegrationTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CookieCsrfTokenRepository repository = new SecurityBeans().csrfTokenRepository();
        var csrfFilter = new CsrfFilter(repository);
        csrfFilter.setRequestHandler(new SpaCsrfTokenRequestHandler());
        mockMvc = MockMvcBuilders.standaloneSetup(new CsrfProbeController())
                .addFilters(csrfFilter)
                .build();
    }

    @Test
    void csrfEndpointEagerlySetsCookieAndUnsafeRequestsNeedMatchingToken() throws Exception {
        var csrfResponse = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse();
        var csrfCookie = csrfResponse.getCookie("XSRF-TOKEN");
        assertThat(csrfCookie).isNotNull();
        assertThat(csrfCookie.getValue()).isNotBlank();

        mockMvc.perform(post("/api/auth/echo"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/echo")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/auth/echo")
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent());
    }

    @RestController
    static class CsrfProbeController {
        @GetMapping("/api/auth/csrf")
        CsrfToken csrf(HttpServletRequest request) {
            return (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        }

        @PostMapping("/api/auth/echo")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        void echo() {
        }
    }
}
