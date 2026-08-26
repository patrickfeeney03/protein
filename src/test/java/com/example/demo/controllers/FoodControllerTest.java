package com.example.demo.controllers;

import com.example.demo.DTOs.FoodDto;
import com.example.demo.entities.FoodEntity;
import com.example.demo.entities.UserEntity;
import com.example.demo.services.FoodService;
import com.example.demo.services.NutritionScanService;
import com.example.demo.services.ParsedNutrition;
import com.example.demo.services.PriceRefreshService;
import com.example.demo.services.ProductDetails;
import com.example.demo.services.RawNutrient;
import com.example.demo.services.ScanResult;
import com.example.demo.services.ScanSource;
import com.example.demo.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FoodControllerTest {
    private final FoodService foodService = mock(FoodService.class);
    private final NutritionScanService nutritionScanService = mock(NutritionScanService.class);
    private final PriceRefreshService priceRefreshService = mock(PriceRefreshService.class);
    private final UserService userService = mock(UserService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FoodController(foodService, nutritionScanService, priceRefreshService, userService))
                .build();
    }

    @Test
    void addFood_passesFoodRequestToService() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        var response = new FoodDto();
        response.setId(99L);
        when(foodService.addFood(org.mockito.ArgumentMatchers.any(FoodEntity.FoodRequest.class), eq(5L)))
                .thenReturn(response);

        var foodRequest = new FoodEntity.FoodRequest(
                "Oats",
                "Brand",
                40f,
                FoodEntity.Unit.G,
                null,
                1f,
                142f,
                4.9f,
                24.7f,
                1.7f,
                null,
                null,
                null,
                null,
                false
        );

        mockMvc.perform(post("/api/food")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(foodRequest))
                .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99L));

        ArgumentCaptor<FoodEntity.FoodRequest> requestCaptor = ArgumentCaptor.forClass(FoodEntity.FoodRequest.class);
        verify(foodService).addFood(requestCaptor.capture(), eq(5L));
        assertEquals(foodRequest, requestCaptor.getValue());
    }

    @Test
    void addFood_withoutAuthentication_returnsUnauthorized() throws Exception {
        var foodRequest = new FoodEntity.FoodRequest(
                "Oats",
                "Brand",
                40f,
                FoodEntity.Unit.G,
                null,
                1f,
                142f,
                4.9f,
                24.7f,
                1.7f,
                null,
                null,
                null,
                null,
                false
        );

        mockMvc.perform(post("/api/food")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(foodRequest)))
                .andExpect(status().isUnauthorized());

        verify(foodService, never()).addFood(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getAllFoods_allowsAnonymousAccess() throws Exception {
        var response = new FoodDto();
        response.setId(12L);
        when(foodService.getAllFoods(null)).thenReturn(List.of(response));

        mockMvc.perform(get("/api/food")
                        .principal(new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(12L));

        verify(foodService).getAllFoods(null);
    }

    @Test
    void getFood_allowsAnonymousAccess() throws Exception {
        var response = new FoodDto();
        response.setId(21L);
        when(foodService.getFoodAsDto(21L, null)).thenReturn(response);

        mockMvc.perform(get("/api/food/21")
                        .principal(new AnonymousAuthenticationToken("key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(21L));

        verify(foodService).getFoodAsDto(21L, null);
    }

    @Test
    void publicFoodReads_ignoreMissingAuthenticatedUsers() throws Exception {
        when(userService.getByEmail("ghost@example.com")).thenReturn(Optional.empty());

        var food = new FoodDto();
        food.setId(33L);
        when(foodService.getAllFoods(null)).thenReturn(List.of(food));
        when(foodService.getFoodAsDto(33L, null)).thenReturn(food);

        var authentication = new UsernamePasswordAuthenticationToken("ghost@example.com", "n/a", List.of());

        mockMvc.perform(get("/api/food").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(33L));

        mockMvc.perform(get("/api/food/33").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(33L));

        verify(foodService).getAllFoods(null);
        verify(foodService).getFoodAsDto(33L, null);
    }

    @Test
    void scanImage_returnsScannerPreview() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));
        when(nutritionScanService.scan(anyList())).thenReturn(
                new ScanResult(
                        true,
                        List.of("warning"),
                        List.of("disagreement"),
                        List.of("product-disagreement"),
                        ScanSource.RAW_TABLE,
                        false,
                        ScanSource.RAW_TEXT,
                        true,
                        new ParsedNutrition(
                                40f,
                                FoodEntity.Unit.G,
                                142f,
                                354f,
                                4.9f,
                                12.2f,
                                24.7f,
                                61.7f,
                                1.7f,
                                4.1f
                        ),
                        new ProductDetails("Oats", "Brand", null, "Aldi", 3f, 1200f, FoodEntity.Unit.G, null, null),
                        java.util.Map.of("SERVING_SIZE", new RawNutrient("(40g)"))
                )
        );

        var firstImagePart = new MockMultipartFile("images", "label-front.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2});
        var secondImagePart = new MockMultipartFile("images", "label-back.png", MediaType.IMAGE_PNG_VALUE, new byte[]{3, 4});

        mockMvc.perform(multipart("/api/food/scan-image")
                        .file(firstImagePart)
                        .file(secondImagePart)
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanSucceeded").value(true))
                .andExpect(jsonPath("$.warnings[0]").value("warning"))
                .andExpect(jsonPath("$.disagreements[0]").value("disagreement"))
                .andExpect(jsonPath("$.productDisagreements[0]").value("product-disagreement"))
                .andExpect(jsonPath("$.sourceUsed").value("RAW_TABLE"))
                .andExpect(jsonPath("$.productSourceUsed").value("RAW_TEXT"))
                .andExpect(jsonPath("$.product.name").value("Oats"))
                .andExpect(jsonPath("$.product.totalWeight").value(1200.0))
                .andExpect(jsonPath("$.product.drainedWeight").doesNotExist())
                .andExpect(jsonPath("$.product.servingsPerContainer100").value(12.0))
                .andExpect(jsonPath("$.parsed.caloriesPer100").value(354.0))
                .andExpect(jsonPath("$.rawNutrients.SERVING_SIZE.text").value("(40g)"));

        verify(nutritionScanService).scan(anyList());
    }

    @Test
    void scanImage_ignoresImagesAfterFirstThree() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));
        when(nutritionScanService.scan(anyList())).thenReturn(ScanResult.failed(List.of()));

        var firstImagePart = new MockMultipartFile("images", "label-front.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1});
        var secondImagePart = new MockMultipartFile("images", "label-back.png", MediaType.IMAGE_PNG_VALUE, new byte[]{2});
        var thirdImagePart = new MockMultipartFile("images", "label-side.png", MediaType.IMAGE_PNG_VALUE, new byte[]{3});
        var fourthImagePart = new MockMultipartFile("images", "label-extra.png", MediaType.IMAGE_PNG_VALUE, new byte[]{4});

        mockMvc.perform(multipart("/api/food/scan-image")
                        .file(firstImagePart)
                        .file(secondImagePart)
                        .file(thirdImagePart)
                        .file(fourthImagePart)
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk());

        ArgumentCaptor<List<MultipartFile>> imageCaptor = ArgumentCaptor.forClass(List.class);
        verify(nutritionScanService).scan(imageCaptor.capture());
        assertEquals(3, imageCaptor.getValue().size());
        assertEquals("label-front.png", imageCaptor.getValue().get(0).getOriginalFilename());
        assertEquals("label-back.png", imageCaptor.getValue().get(1).getOriginalFilename());
        assertEquals("label-side.png", imageCaptor.getValue().get(2).getOriginalFilename());
    }

    @Test
    void refreshAllPrices_rejectsNonAdminUsers() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/food/admin/refresh-prices")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isForbidden());

        verifyNoInteractions(priceRefreshService);
    }

    @Test
    void refreshFoodPrice_allowsAdminUsers() throws Exception {
        var admin = new UserEntity();
        admin.setId(7L);
        admin.setAdmin(true);
        when(userService.getByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        var response = new PriceRefreshService.RefreshFoodResult(11L, "aldi", true);
        when(priceRefreshService.refreshFood(11L, 7L)).thenReturn(response);
        when(foodService.getFoodAsDto(11L, 7L)).thenReturn(new FoodDto());

        mockMvc.perform(post("/api/food/admin/11/refresh-price")
                        .principal(new UsernamePasswordAuthenticationToken("admin@example.com", "n/a", List.of())))
                .andExpect(status().isOk());

        verify(priceRefreshService).refreshFood(11L, 7L);
        verify(foodService).getFoodAsDto(11L, 7L);
    }

    @Test
    void addFavorite_returnsNoContent() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(post("/api/food/99/favorite")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isNoContent());

        verify(foodService).addFavorite(99L, 5L);
    }

    @Test
    void addFavorite_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/food/99/favorite"))
                .andExpect(status().isUnauthorized());

        verify(foodService, never()).addFavorite(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeFavorite_returnsNoContent() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/food/99/favorite")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isNoContent());

        verify(foodService).removeFavorite(99L, 5L);
    }

    @Test
    void removeFavorite_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/food/99/favorite"))
                .andExpect(status().isUnauthorized());

        verify(foodService, never()).removeFavorite(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateFood_passesUpdateRequestToService() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        var response = new FoodDto();
        response.setId(99L);
        when(foodService.update(org.mockito.ArgumentMatchers.any(FoodEntity.UpdateRequest.class), eq(5L)))
                .thenReturn(response);

        var updateRequest = new FoodEntity.UpdateRequest(
                99L, "Oats", "Brand",
                40f, FoodEntity.Unit.G, null,
                1f, 142f, 4.9f, 24.7f, 1.7f,
                null, null, null, null
        );

        mockMvc.perform(put("/api/food")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateRequest))
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(99L));

        ArgumentCaptor<FoodEntity.UpdateRequest> requestCaptor = ArgumentCaptor.forClass(FoodEntity.UpdateRequest.class);
        verify(foodService).update(requestCaptor.capture(), eq(5L));
        assertEquals(updateRequest, requestCaptor.getValue());
    }

    @Test
    void updateFood_withoutAuthentication_returnsUnauthorized() throws Exception {
        var updateRequest = new FoodEntity.UpdateRequest(
                99L, "Oats", "Brand",
                40f, FoodEntity.Unit.G, null,
                1f, 142f, 4.9f, 24.7f, 1.7f,
                null, null, null, null
        );

        mockMvc.perform(put("/api/food")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(updateRequest)))
                .andExpect(status().isUnauthorized());

        verify(foodService, never()).update(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deleteFood_deletesFood() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(delete("/api/food/99")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk());

        verify(foodService).deleteFood(99L, 5L);
    }

    @Test
    void deleteFood_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/food/99"))
                .andExpect(status().isUnauthorized());

        verify(foodService, never()).deleteFood(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getFavorites_returnsFavoriteFoods() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        var food = new FoodDto();
        food.setId(88L);
        food.setName("Favorites Oats");
        when(foodService.getFavorites(5L)).thenReturn(List.of(food));

        mockMvc.perform(get("/api/food/favorites")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(88L))
                .andExpect(jsonPath("$[0].name").value("Favorites Oats"));

        verify(foodService).getFavorites(5L);
    }

    @Test
    void getFavorites_withoutAuthentication_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/food/favorites"))
                .andExpect(status().isUnauthorized());

        verify(foodService, never()).getFavorites(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scanImage_withoutImages_returnsBadRequest() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        mockMvc.perform(multipart("/api/food/scan-image")
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isBadRequest());

        verify(nutritionScanService, never()).scan(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void scanImage_withSingleImageParam_returnsScannerPreview() throws Exception {
        var user = new UserEntity();
        user.setId(5L);
        when(userService.getByEmail("patrick@example.com")).thenReturn(Optional.of(user));

        var scanResult = new ScanResult(
                true, List.of(), List.of(), List.of(),
                ScanSource.RAW_TABLE, false, ScanSource.RAW_TEXT, true,
                new ParsedNutrition(40f, FoodEntity.Unit.G, 142f, 354f, 4.9f, 12.2f, 24.7f, 61.7f, 1.7f, 4.1f),
                new ProductDetails("Oats", "Brand", null, "Aldi", 3f, 1200f, FoodEntity.Unit.G, null, null),
                java.util.Map.of()
        );
        when(nutritionScanService.scan(anyList())).thenReturn(scanResult);

        var imagePart = new MockMultipartFile("image", "label.png", MediaType.IMAGE_PNG_VALUE, new byte[]{1, 2});

        mockMvc.perform(multipart("/api/food/scan-image")
                        .file(imagePart)
                        .principal(new UsernamePasswordAuthenticationToken("patrick@example.com", "n/a", List.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanSucceeded").value(true));

        verify(nutritionScanService).scan(anyList());
    }
}
