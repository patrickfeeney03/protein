package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GenericUserDataControllerTest {
    private static final String TEST_JSON = "{\"value\":1}";
    private final GenericUserDataRepository repo = mock(GenericUserDataRepository.class);
    private final UserService userService = mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GenericUserDataController(repo, userService))
                .build();
    }

    @Test
    void getData_requiresAdminAccess() throws Exception {
        var admin = new UserEntity();
        admin.setAdmin(true);
        when(userService.getByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        var data = new GenericUserData("{\"value\":1}");
        when(repo.findAll()).thenReturn(List.of(data));

        mockMvc.perform(get("/api")
                        .principal(new UsernamePasswordAuthenticationToken("admin@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].json").value("{\"value\":1}"));

        verify(repo).findAll();
    }

    @Test
    void getData_withoutAdminAccess_returnsForbidden() throws Exception {
        var user = new UserEntity();
        user.setAdmin(false);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isForbidden());

        verify(repo, never()).findAll();
    }

    @Test
    void getData_withStaleAuthenticatedPrincipal_returnsUnauthorized() throws Exception {
        when(userService.getByEmail("ghost@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api")
                        .principal(new UsernamePasswordAuthenticationToken("ghost@example.com", "n/a", List.of())))
                .andExpect(status().isUnauthorized());

        verify(repo, never()).findAll();
    }

    @Test
    void getData_withAnonymousAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api")
                        .principal(new AnonymousAuthenticationToken(
                                "key",
                                "anonymousUser",
                                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))))
                .andExpect(status().isUnauthorized());

        verify(repo, never()).findAll();
    }

    @Test
    void getData_withUnauthenticatedPrincipal_returnsUnauthorized() throws Exception {
        var token = new UsernamePasswordAuthenticationToken("user@example.com", "n/a");
        token.setAuthenticated(false);

        mockMvc.perform(get("/api")
                        .principal(token))
                .andExpect(status().isUnauthorized());

        verify(repo, never()).findAll();
    }

    @Test
    void getData_withNullName_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api")
                        .principal(new UsernamePasswordAuthenticationToken(null, "n/a", List.of())))
                .andExpect(status().isUnauthorized());

        verify(repo, never()).findAll();
    }

    @Test
    void getData_withBlankName_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api")
                        .principal(new UsernamePasswordAuthenticationToken("", "n/a", List.of())))
                .andExpect(status().isUnauthorized());

        verify(repo, never()).findAll();
    }

    @Test
    void addData_withoutAuthentication_returnsUnauthorized() throws Exception {
        var body = new GenericUserData(TEST_JSON);

        mockMvc.perform(post("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isUnauthorized());

        verify(repo, never()).save(any());
    }

    @Test
    void addData_stripsClientSuppliedIdBeforeSaving() throws Exception {
        var admin = new UserEntity();
        admin.setAdmin(true);
        when(userService.getByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        var body = new GenericUserData("{\"value\":1}");
        body.setId(42L);
        when(repo.save(any(GenericUserData.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body))
                        .principal(new UsernamePasswordAuthenticationToken("admin@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.json").value("{\"value\":1}"))
                .andExpect(jsonPath("$.id").value(nullValue()));

        var captor = org.mockito.ArgumentCaptor.forClass(GenericUserData.class);
        verify(repo).save(captor.capture());
        assertNull(captor.getValue().getId());
    }
}
