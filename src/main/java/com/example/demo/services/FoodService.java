package com.example.demo.services;

import com.example.demo.DTOs.FoodDto;
import com.example.demo.RetailerUrlPolicy;
import com.example.demo.controllers.errors.DedupCandidateDto;
import com.example.demo.dedup.DedupCandidate;
import com.example.demo.dedup.ExactDuplicateFoodException;
import com.example.demo.dedup.FoodDedupService;
import com.example.demo.dedup.PossibleDuplicateFoodException;
import com.example.demo.entities.FavoriteFoodEntity;
import com.example.demo.entities.FoodEntity;
import com.example.demo.entities.UserEntity;
import com.example.demo.repositories.FavoriteFoodRepository;
import com.example.demo.repositories.FoodRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class FoodService {
    private final FoodRepository foodRepository;
    private final UserService userService;
    private final com.example.demo.AldiPriceFetcher aldiPriceFetcher;
    private final com.example.demo.LidlPriceFetcher lidlPriceFetcher;
    private final com.example.demo.DunnesPriceFetcher dunnesPriceFetcher;
    private final com.example.demo.TescoPriceFetcher tescoPriceFetcher;
    private final CommentService commentService;
    private final FavoriteFoodRepository favoriteFoodRepository;
    private final FoodDedupService foodDedupService;

    public FoodService(
            FoodRepository foodRepository,
            UserService userService,
            com.example.demo.AldiPriceFetcher aldiPriceFetcher,
            com.example.demo.LidlPriceFetcher lidlPriceFetcher,
            com.example.demo.DunnesPriceFetcher dunnesPriceFetcher,
            com.example.demo.TescoPriceFetcher tescoPriceFetcher,
            CommentService commentService,
            FavoriteFoodRepository favoriteFoodRepository,
            FoodDedupService foodDedupService
    ) {
        this.foodRepository = foodRepository;
        this.userService = userService;
        this.aldiPriceFetcher = aldiPriceFetcher;
        this.lidlPriceFetcher = lidlPriceFetcher;
        this.dunnesPriceFetcher = dunnesPriceFetcher;
        this.tescoPriceFetcher = tescoPriceFetcher;
        this.commentService = commentService;
        this.favoriteFoodRepository = favoriteFoodRepository;
        this.foodDedupService = foodDedupService;
    }

    public FoodDto addFood(FoodEntity.FoodRequest fr, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }
        validateFoodRequest(fr);
        if (fr.servingSize() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing serving size");
        }

        // Find by using product code/key
        var canonicalProductKey = foodDedupService.computeCanonicalProductKey(fr.productUrl()).orElse(null);
        if (canonicalProductKey != null) {
            var existingExact = foodDedupService.findExactDuplicate(canonicalProductKey);
            if (existingExact.isPresent()) {
                throw new ExactDuplicateFoodException(canonicalProductKey, existingExact.get().getId());
            }
        }

        Float resolvedPrice = fr.price();
        String priceSource = resolvedPrice != null ? "MANUAL" : null;
        java.time.Instant priceLastVerifiedAt = resolvedPrice != null ? java.time.Instant.now() : null;
        List<String> imageUrls = List.of();
        var storePricing = fetchStorePricing(fr.productUrl());
        if (storePricing.isPresent()) {
            var fetched = storePricing.get();
            imageUrls = fetched.imageUrls();
            if (fetched.price() != null) {
                resolvedPrice = fetched.price();
                priceSource = fetched.priceSource();
                priceLastVerifiedAt = java.time.Instant.now();
            }
        }

        Float caloriesPer100 = getPer100(fr.caloriesPerServing(), fr.servingSize());
        Float proteinPer100 = getPer100(fr.proteinPerServing(), fr.servingSize());
        Float carbsPer100 = getPer100(fr.carbsPerServing(), fr.servingSize());
        Float fatPer100 = getPer100(fr.fatPerServing(), fr.servingSize());
        Float totalWeight = getTotalWeight(fr.servingSize(), fr.servingsPerContainer());

        var shouldForceCreate = Boolean.TRUE.equals(fr.forceCreate());
        if (!shouldForceCreate) {
            var possibleDuplicates = foodDedupService.findLikelyDuplicates(
                    fr,
                    caloriesPer100,
                    proteinPer100,
                    carbsPer100,
                    fatPer100,
                    totalWeight,
                    resolvedPrice
            );
            if (foodDedupService.isPossibleDuplicate(possibleDuplicates)) {
                throw new PossibleDuplicateFoodException(this.toDedupCandidateDtos(possibleDuplicates, userId));
            }
        }

        var proteinPerEuro = this.getProteinPerEuro(
                fr.proteinPerServing(),
                fr.servingsPerContainer(),
                resolvedPrice);

        var foodEntity = new FoodEntity(
                fr.name(), fr.brand(),
                caloriesPer100, proteinPer100, carbsPer100, fatPer100,
                fr.servingSize(), fr.servingUnit(), totalWeight,
                resolvedPrice,
                fr.servingsPerContainer(),
                userId,
                proteinPerEuro,
                fr.extraInfo(),
                fr.caloriesPerServing(), fr.proteinPerServing(), fr.carbsPerServing(), fr.fatPerServing(),
                fr.storeName(), fr.buyDate(),
                fr.productUrl(),
                canonicalProductKey,
                priceLastVerifiedAt,
                priceSource,
                imageUrls);

        var savedFood = this.foodRepository.save(foodEntity);

        var user = this.userService.getOrThrow(userId);
        return toDtoForUser(savedFood, userId, user);
    }

    public List<FoodDto> getAllFoods(Long userId) {
        var user = resolveUser(userId);
        return this.foodRepository.findAll().stream()
                .map(food -> toDtoForViewer(food, userId, user))
                .toList();
    }

    public FoodDto getFoodAsDto(Long foodId, Long userId) {
        var user = resolveUser(userId);
        return toDtoForViewer(this.findOrThrow(foodId), userId, user);
    }

    public void addFavorite(Long foodId, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }
        this.userService.getOrThrow(userId);
        if (!this.foodRepository.existsById(foodId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (this.favoriteFoodRepository.existsByUserIdAndFoodId(userId, foodId)) {
            return;
        }

        var favorite = new FavoriteFoodEntity();
        favorite.setUserId(userId);
        favorite.setFoodId(foodId);
        this.favoriteFoodRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(Long foodId, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }
        this.userService.getOrThrow(userId);
        if (!this.foodRepository.existsById(foodId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        this.favoriteFoodRepository.deleteByUserIdAndFoodId(userId, foodId);
    }

    public List<FoodDto> getFavorites(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }

        var user = this.userService.getOrThrow(userId);
        var favorites = this.favoriteFoodRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        var favoriteIds = favorites.stream().map(FavoriteFoodEntity::getFoodId).toList();

        var foodsById = this.foodRepository.findAllById(favoriteIds).stream()
                .collect(Collectors.toMap(FoodEntity::getId, food -> food));

        return favoriteIds.stream()
                .map(foodsById::get)
                .filter(Objects::nonNull)
                .map(food -> {
                    var dto = this.toDtoForUser(food, userId, user);
                    dto.setIsFavorite(true);
                    return dto;
                })
                .toList();
    }

    @Transactional
    public void deleteAllFavoritesForFood(Long foodId) {
        this.favoriteFoodRepository.deleteAllByFoodId(foodId);
    }

    private FoodEntity findOrThrow(Long id) {
        return this.foodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }


    private FoodDto toDto(FoodEntity e, boolean isAdmin) {
        var dto = new FoodDto();
        if (isAdmin) {
            dto.setUserId(e.getUserId());
        }

        dto.setId(e.getId());

        dto.setName(e.getName());
        dto.setBrand(e.getBrand());

        dto.setCaloriesPerServing(e.getCaloriesPerServing());
        dto.setProteinPerServing(e.getProteinPerServing());
        dto.setCarbsPerServing(e.getCarbsPerServing());
        dto.setFatPerServing(e.getFatPerServing());

        dto.setCaloriesPer100(e.getCaloriesPer100());
        dto.setProteinPer100(e.getProteinPer100());
        dto.setCarbsPer100(e.getCarbsPer100());
        dto.setFatPer100(e.getFatPer100());

        dto.setServingsPerContainer(e.getServingsPerContainer());
        dto.setServingSize(e.getServingSize());
        dto.setServingUnit(e.getServingUnit());
        dto.setTotalWeight(e.getTotalWeight());

        dto.setPrice(e.getPrice());
        dto.setProteinPerEuro(e.getProteinPerEuro());

        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());

        dto.setExtraInfo(e.getExtraInfo());

        dto.setStoreName(e.getStoreName());
        dto.setBuyDate(e.getBuyDate());
        dto.setProductUrl(e.getProductUrl());
        dto.setPriceLastVerifiedAt(e.getPriceLastVerifiedAt());
        dto.setPriceSource(e.getPriceSource());
        dto.setImageUrls(e.getImageUrls());

        return dto;
    }

    private Float getPer100(Float macro, Float servingSize) {
        var sizing = 100;
        var ratio = servingSize / sizing;
        return macro / ratio;
    }

    private Float getProteinPerEuro(Float protein, Float servingsPerContainer, Float price) {
        if (protein == null || servingsPerContainer == null || price == null || price == 0) {
            return null;
        }
        var totalProtein = protein * servingsPerContainer;
        return totalProtein / price;
    }

    private Optional<StorePricing> fetchStorePricing(String productUrl) {
        if (productUrl == null) {
            return Optional.empty();
        }

        return RetailerUrlPolicy.retailerFor(productUrl)
                .flatMap(retailer -> switch (retailer) {
                    case ALDI -> fetchStorePricing(productUrl, new StoreFetcher<>(
                            RetailerUrlPolicy.Retailer.ALDI,
                            aldiPriceFetcher::fetchProductData,
                            com.example.demo.AldiPriceFetcher.AldiProductData::price,
                            com.example.demo.AldiPriceFetcher.AldiProductData::imageUrls,
                            "ALDI_API"));
                    case LIDL -> fetchStorePricing(productUrl, new StoreFetcher<>(
                            RetailerUrlPolicy.Retailer.LIDL,
                            lidlPriceFetcher::fetchProductData,
                            com.example.demo.LidlPriceFetcher.LidlProductData::price,
                            com.example.demo.LidlPriceFetcher.LidlProductData::imageUrls,
                            "LIDL_HTML"));
                    case DUNNES -> fetchStorePricing(productUrl, new StoreFetcher<>(
                            RetailerUrlPolicy.Retailer.DUNNES,
                            dunnesPriceFetcher::fetchProductData,
                            com.example.demo.DunnesPriceFetcher.DunnesProductData::price,
                            com.example.demo.DunnesPriceFetcher.DunnesProductData::imageUrls,
                            "DUNNES_HTML"));
                    case TESCO -> fetchStorePricing(productUrl, new StoreFetcher<>(
                            RetailerUrlPolicy.Retailer.TESCO,
                            tescoPriceFetcher::fetchProductData,
                            com.example.demo.TescoPriceFetcher.TescoProductData::price,
                            com.example.demo.TescoPriceFetcher.TescoProductData::imageUrls,
                            "TESCO_GQL"));
                });
    }

    private <T> Optional<StorePricing> fetchStorePricing(String productUrl, StoreFetcher<T> config) {
        if (!RetailerUrlPolicy.isAllowedRetailerUrl(productUrl, config.retailer())) {
            return Optional.empty();
        }

        return config.fetcher().apply(productUrl)
                .map(data -> new StorePricing(
                        config.priceExtractor().apply(data),
                        config.imageUrlsExtractor().apply(data),
                        config.priceSource()
                ));
    }

    private record StoreFetcher<T>(
            RetailerUrlPolicy.Retailer retailer,
            Function<String, Optional<T>> fetcher,
            Function<T, Float> priceExtractor,
            Function<T, List<String>> imageUrlsExtractor,
            String priceSource
    ) {}

    private record StorePricing(Float price, List<String> imageUrls, String priceSource) {}

    private void validateFoodRequest(FoodEntity.FoodRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing food request");
        }
        validateBounds(request.name(), request.brand(), request.extraInfo(), request.storeName(), request.productUrl(),
                request.servingSize(), request.servingsPerContainer(), request.price(), request.caloriesPerServing(),
                request.proteinPerServing(), request.carbsPerServing(), request.fatPerServing());
    }

    private void validateBounds(
            String name,
            String brand,
            String extraInfo,
            String storeName,
            String productUrl,
            Float servingSize,
            Float servingsPerContainer,
            Float price,
            Float calories,
            Float protein,
            Float carbs,
            Float fat
    ) {
        if (name == null || name.length() > 200 || brand == null || brand.length() > 200
                || (extraInfo != null && extraInfo.length() > 5000)
                || (storeName != null && storeName.length() > 100)
                || (productUrl != null && productUrl.length() > 2048)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Food field exceeds its maximum length");
        }
        validateNumber("serving size", servingSize, 0.0001f, 1_000_000f);
        validateNumber("servings per container", servingsPerContainer, 0f, 1_000_000f);
        validateNumber("price", price, 0f, 1_000_000f);
        validateNumber("calories", calories, 0f, 1_000_000f);
        validateNumber("protein", protein, 0f, 1_000_000f);
        validateNumber("carbohydrates", carbs, 0f, 1_000_000f);
        validateNumber("fat", fat, 0f, 1_000_000f);
    }

    private void validateNumber(String field, Float value, float minimum, float maximum) {
        if (value != null && (!Float.isFinite(value) || value < minimum || value > maximum)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        }
    }

    private Float getTotalWeight(Float servingSize, Float servingsPerContainer) {
        if (servingSize == null || servingsPerContainer == null) {
            return null;
        }
        return servingSize * servingsPerContainer;
    }

    private UserEntity resolveUser(Long userId) {
        return userId == null ? null : this.userService.getOrThrow(userId);
    }

    private UserEntity requireEditAccess(Long ownerUserId, Long userId) {
        var user = this.userService.get(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found in DB"));

        if (!Objects.equals(ownerUserId, userId) && !user.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return user;
    }

    private List<DedupCandidateDto> toDedupCandidateDtos(List<DedupCandidate> candidates, Long userId) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        var user = this.userService.getOrThrow(userId);
        var ids = candidates.stream().map(DedupCandidate::foodId).toList();

        Map<Long, FoodEntity> entitiesById = this.foodRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(FoodEntity::getId, entity -> entity));

        return candidates.stream()
                .map(candidate -> {
                    var entity = entitiesById.get(candidate.foodId());
                    if (entity == null) {
                        return null;
                    }
                    var dto = this.toDtoForUser(entity, userId, user);
                    return new DedupCandidateDto(dto, candidate.score(), candidate.matchReasons());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public void deleteFood(Long id, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }

        var persisted = this.foodRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        requireEditAccess(persisted.getUserId(), userId);

        this.deleteAllFavoritesForFood(persisted.getId());
        this.commentService.deleteAllForFood(persisted.getId());

        this.foodRepository.deleteById(id);
    }

    public FoodDto update(FoodEntity.UpdateRequest uR, Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No user Id");
        }
        if (uR == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing food request");
        }
        if (uR.id() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing food id");
        }
        if (uR.servingSize() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing serving size");
        }
        validateBounds(uR.name(), uR.brand(), uR.extraInfo(), uR.storeName(), uR.productUrl(),
                uR.servingSize(), uR.servingsPerContainer(), uR.price(), uR.caloriesPerServing(),
                uR.proteinPerServing(), uR.carbsPerServing(), uR.fatPerServing());

        var persistedEntity = this.foodRepository.findById(uR.id());
        if (persistedEntity.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        var entity = persistedEntity.get();
        var user = requireEditAccess(entity.getUserId(), userId);

        Float caloriesPer100 = getPer100(uR.caloriesPerServing(), uR.servingSize());
        Float proteinPer100 = getPer100(uR.proteinPerServing(), uR.servingSize());
        Float carbsPer100 = getPer100(uR.carbsPerServing(), uR.servingSize());
        Float fatPer100 = getPer100(uR.fatPerServing(), uR.servingSize());

        var proteinPerEuro = this.getProteinPerEuro(
                uR.proteinPerServing(),
                uR.servingsPerContainer(),
                uR.price());

        entity.setName(uR.name());
        entity.setBrand(uR.brand());

        entity.setCaloriesPer100(caloriesPer100);
        entity.setProteinPer100(proteinPer100);
        entity.setCarbsPer100(carbsPer100);
        entity.setFatPer100(fatPer100);

        entity.setCaloriesPerServing(uR.caloriesPerServing());
        entity.setProteinPerServing(uR.proteinPerServing());
        entity.setCarbsPerServing(uR.carbsPerServing());
        entity.setFatPerServing(uR.fatPerServing());

        entity.setServingSize(uR.servingSize());
        entity.setServingUnit(uR.servingUnit());
        entity.setTotalWeight(getTotalWeight(uR.servingSize(), uR.servingsPerContainer()));
        entity.setPrice(uR.price());
        entity.setServingsPerContainer(uR.servingsPerContainer());
        entity.setProteinPerEuro(proteinPerEuro);
        entity.setExtraInfo(uR.extraInfo());

        entity.setStoreName(uR.storeName());
        entity.setBuyDate(uR.buyDate());
        entity.setProductUrl(uR.productUrl());
        entity.setCanonicalProductKey(foodDedupService.computeCanonicalProductKey(uR.productUrl()).orElse(null));
        if (uR.price() != null) {
            entity.setPriceLastVerifiedAt(java.time.Instant.now());
            entity.setPriceSource("MANUAL");
        }

        var updatedFood = this.foodRepository.save(entity);
        return toDtoForUser(updatedFood, userId, user);
    }

    private FoodDto toDtoForViewer(FoodEntity food, Long userId, UserEntity user) {
        if (user == null) {
            return toDto(food, false);
        }
        return toDtoForUser(food, userId, user);
    }

    private FoodDto toDtoForUser(FoodEntity food, Long userId, UserEntity user) {
        return toDto(food, isOwnerOrAdmin(user, userId, food.getUserId()));
    }

    private boolean isOwnerOrAdmin(UserEntity user, Long userId, Long ownerId) {
        return user.isAdmin() || Objects.equals(userId, ownerId);
    }
}
