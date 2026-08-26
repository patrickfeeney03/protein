package com.example.demo.controllers;

import com.example.demo.DTOs.FoodDto;
import com.example.demo.entities.FoodEntity;
import com.example.demo.MultipartImageNormalizer;
import com.example.demo.services.FoodService;
import com.example.demo.services.NutritionScanService;
import com.example.demo.services.PriceRefreshService;
import com.example.demo.services.ScanResult;
import com.example.demo.services.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/food")
public class FoodController {
    private final FoodService foodService;
    private final NutritionScanService nutritionScanService;
    private final PriceRefreshService priceRefreshService;
    private final UserService userService;

    public FoodController(FoodService foodService, NutritionScanService nutritionScanService, PriceRefreshService priceRefreshService, UserService userService) {
        this.foodService = foodService;
        this.nutritionScanService = nutritionScanService;
        this.priceRefreshService = priceRefreshService;
        this.userService = userService;
    }

    @PostMapping
    public FoodDto addFood(@Valid @RequestBody FoodEntity.FoodRequest foodRequest, Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication, true);
        return this.foodService.addFood(foodRequest, userId);
    }

    @PostMapping(path = "/scan-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ScanResult scanImage(
            @RequestPart(name = "images", required = false) List<MultipartFile> images,
            @RequestPart(name = "image", required = false) MultipartFile image,
            Authentication authentication
    ) {
        getUserIdFromAuthentication(authentication, true);
        return this.nutritionScanService.scan(normalizeImages(images, image));
    }

    @GetMapping
    public List<FoodDto> getAllFoods(Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, false);
        return this.foodService.getAllFoods(userId);
    }

    @GetMapping("/favorites")
    public List<FoodDto> getFavorites(Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, true);
        return this.foodService.getFavorites(userId);
    }

    @PostMapping("/{foodId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addFavorite(@PathVariable Long foodId, Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, true);
        this.foodService.addFavorite(foodId, userId);
    }

    @DeleteMapping("/{foodId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFavorite(@PathVariable Long foodId, Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, true);
        this.foodService.removeFavorite(foodId, userId);
    }

    @GetMapping("/{id}")
    public FoodDto getFood(@PathVariable Long id, Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, false);
        return this.foodService.getFoodAsDto(id, userId);
    }

    @DeleteMapping("/{id}")
    public void deleteFood(@PathVariable Long id, Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, true);
        this.foodService.deleteFood(id, userId);
    }

    @PutMapping
    public FoodDto updateFood(@Valid @RequestBody FoodEntity.UpdateRequest updateRequest, Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, true);
        return this.foodService.update(updateRequest, userId);
    }

    @PostMapping("/admin/refresh-prices")
    public PriceRefreshService.RefreshAllResult refreshAllPrices(Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, true, true);
        return this.priceRefreshService.refreshAllFoods(userId);
    }

    @PostMapping("/admin/{id}/refresh-price")
    public RefreshFoodResponse refreshFoodPrice(@PathVariable Long id, Authentication authentication) {
        var userId = getUserIdFromAuthentication(authentication, true, true);
        var refreshResult = this.priceRefreshService.refreshFood(id, userId);
        var food = this.foodService.getFoodAsDto(id, userId);
        return new RefreshFoodResponse(refreshResult.foodId(), refreshResult.store(), refreshResult.updated(), food);
    }

    private Long getUserIdFromAuthentication(Authentication authentication, boolean required) {
        return getUserIdFromAuthentication(authentication, required, false);
    }

    private Long getUserIdFromAuthentication(Authentication authentication, boolean required, boolean adminRequired) {
        if (isAnonymous(authentication)) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            return null;
        }

        var email = authentication.getName();
        if (email == null || email.isBlank()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            return null;
        }

        var user = userService.getByEmail(email);
        if (user.isEmpty()) {
            if (required) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
            }
            return null;
        }

        if (adminRequired && !user.get().isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return user.get().getId();
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken;
    }

    public record RefreshFoodResponse(Long foodId, String store, boolean updated, FoodDto food) {
    }

    private List<MultipartFile> normalizeImages(List<MultipartFile> images, MultipartFile image) {
        var normalized = MultipartImageNormalizer.normalize(images, image);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload at least one image");
        }
        return normalized;
    }
}
