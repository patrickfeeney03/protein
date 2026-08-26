package com.example.demo;

import com.example.demo.entities.UserEntity;
import com.example.demo.services.NutritionScanService;
import com.example.demo.services.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LocalTestingControllerTest {
    private final AldiFoodScraper aldiFoodScraper = mock(AldiFoodScraper.class);
    private final AldiPriceFetcher aldiPriceFetcher = mock(AldiPriceFetcher.class);
    private final AldiPriceUpdater aldiPriceUpdater = mock(AldiPriceUpdater.class);
    private final LidlPriceFetcher lidlPriceFetcher = mock(LidlPriceFetcher.class);
    private final LidlPriceUpdater lidlPriceUpdater = mock(LidlPriceUpdater.class);
    private final DunnesPriceFetcher dunnesPriceFetcher = mock(DunnesPriceFetcher.class);
    private final DunnesPriceUpdater dunnesPriceUpdater = mock(DunnesPriceUpdater.class);
    private final TescoPriceFetcher tescoPriceFetcher = mock(TescoPriceFetcher.class);
    private final TescoPriceUpdater tescoPriceUpdater = mock(TescoPriceUpdater.class);
    private final NutritionScanService nutritionScanService = mock(NutritionScanService.class);
    private final UserService userService = mock(UserService.class);

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LocalTestingController(
                aldiFoodScraper,
                aldiPriceFetcher,
                aldiPriceUpdater,
                lidlPriceFetcher,
                lidlPriceUpdater,
                dunnesPriceFetcher,
                dunnesPriceUpdater,
                tescoPriceFetcher,
                tescoPriceUpdater,
                nutritionScanService,
                userService
        )).build();
    }

    @Test
    void adminEndpointRejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/testing/aldi/price").param("url", "https://aldi.example/1"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(userService, aldiPriceFetcher);
    }

    @Test
    void adminEndpointAllowsAdminUsers() throws Exception {
        var admin = new UserEntity();
        admin.setAdmin(true);
        when(userService.getByEmail("admin@example.com")).thenReturn(Optional.of(admin));
        when(aldiPriceFetcher.fetchPrice("https://aldi.example/1")).thenReturn(Optional.of(1.25f));

        mockMvc.perform(get("/testing/aldi/price")
                        .param("url", "https://aldi.example/1")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "admin@example.com",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isOk());

        verify(userService).getByEmail("admin@example.com");
        verify(aldiPriceFetcher).fetchPrice("https://aldi.example/1");
    }

    @Test
    void adminEndpointWithStaleAuthenticatedPrincipal_returnsUnauthorized() throws Exception {
        when(userService.getByEmail("ghost@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/testing/aldi/price")
                        .param("url", "https://aldi.example/1")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "ghost@example.com",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isUnauthorized());

        verify(userService).getByEmail("ghost@example.com");
        verifyNoInteractions(aldiPriceFetcher);
    }

    @Test
    void adminEndpointWithoutAdminPrivileges_returnsForbidden() throws Exception {
        var user = new UserEntity();
        user.setAdmin(false);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/testing/aldi/price")
                        .param("url", "https://aldi.example/1")
                        .principal(new UsernamePasswordAuthenticationToken(
                                "patrick@example.com",
                                "n/a",
                                List.of(new SimpleGrantedAuthority("ROLE_USER"))
                        )))
                .andExpect(status().isForbidden());

        verify(userService).getByEmail("patrick@example.com");
        verifyNoInteractions(aldiPriceFetcher);
    }
}
