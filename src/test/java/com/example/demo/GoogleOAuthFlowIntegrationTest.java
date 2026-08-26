package com.example.demo;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:sqlite:target/oauth-flow-test.db",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class GoogleOAuthFlowIntegrationTest {

    static final WireMockServer WIRE_MOCK = new WireMockServer(options().dynamicPort());

    static {
        WIRE_MOCK.start();
        WIRE_MOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlPathEqualTo("/oauth/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
        WIRE_MOCK.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/oauth/userinfo"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"sub\":\"google-sub-1\",\"email\":\"person@example.com\",\"name\":\"Person\",\"email_verified\":true}")));
    }

    @AfterAll
    static void stopWireMock() {
        WIRE_MOCK.stop();
    }

    @DynamicPropertySource
    static void oauthProviderProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.client.provider.google.authorization-uri",
                () -> WIRE_MOCK.baseUrl() + "/oauth/authorize");
        registry.add("spring.security.oauth2.client.provider.google.token-uri",
                () -> WIRE_MOCK.baseUrl() + "/oauth/token");
        registry.add("spring.security.oauth2.client.provider.google.user-info-uri",
                () -> WIRE_MOCK.baseUrl() + "/oauth/userinfo");
        registry.add("spring.security.oauth2.client.provider.google.jwk-set-uri",
                () -> WIRE_MOCK.baseUrl() + "/oauth/jwks");
        registry.add("spring.security.oauth2.client.registration.google.scope",
                () -> "profile,email");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void googleLoginFlow_authenticatesAndIssuesRememberMeCookie() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String state = startAuthorization(session);

        MvcResult callback = mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "fake")
                        .param("state", state)
                        .session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/"))
                .andReturn();

        assertThat(callback.getResponse().getCookie("remember-me")).isNotNull();

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("person@example.com"));
    }

    @Test
    void unauthenticatedApiRequest_returns401NotOAuthRedirect() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(result.getResponse().getHeader("Location")).isNull();
    }

    @Test
    void googleLoginFlow_wrongStateDoesNotAuthenticate() throws Exception {
        MockHttpSession session = new MockHttpSession();

        String state = startAuthorization(session);

        mockMvc.perform(get("/api/auth/google/callback")
                        .param("code", "fake")
                        .param("state", "wrong-state-" + state)
                        .session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login?error=oauth"));
    }

    private String startAuthorization(MockHttpSession session) throws Exception {
        MvcResult auth = mockMvc.perform(get("/api/auth/google").session(session))
                .andExpect(status().isFound())
                .andReturn();

        String location = auth.getResponse().getRedirectedUrl();
        assertThat(location).isNotNull();

        var uri = URI.create(location);
        var params = UriComponentsBuilder.fromUri(uri).build().getQueryParams();

        assertThat(params.getFirst("client_id")).isEqualTo("local-dev-placeholder");
        assertThat(params.getFirst("redirect_uri")).isEqualTo("http://localhost:4200/api/auth/google/callback");

        String state = URLDecoder.decode(params.getFirst("state"), StandardCharsets.UTF_8);
        assertThat(state).isNotBlank();
        return state;
    }
}
