package com.example.demo.services;

import com.example.demo.entities.FavoriteFoodEntity;
import com.example.demo.entities.FoodEntity;
import com.example.demo.repositories.FavoriteFoodRepository;
import com.example.demo.repositories.FoodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FoodServiceTest {

    @Mock private FoodRepository foodRepository;
    @Mock private UserService userService;
    @Mock private com.example.demo.AldiPriceFetcher aldiPriceFetcher;
    @Mock private com.example.demo.LidlPriceFetcher lidlPriceFetcher;
    @Mock private com.example.demo.DunnesPriceFetcher dunnesPriceFetcher;
    @Mock private com.example.demo.TescoPriceFetcher tescoPriceFetcher;
    @Mock private CommentService commentService;
    @Mock private FavoriteFoodRepository favoriteFoodRepository;
    @Mock private com.example.demo.dedup.FoodDedupService foodDedupService;

    @InjectMocks
    private FoodService foodService;

    private FoodEntity.FoodRequest baseRequest(String url, Float price, boolean forceCreate) {
        return new FoodEntity.FoodRequest(
                "Name",
                "Brand",
                200f,
                FoodEntity.Unit.G,
                price,
                10f,
                200f,
                20f,
                10f,
                5f,
                null,
                null,
                null,
                url,
                forceCreate
        );
    }

    @Test
    void addFavorite_requiresExistingFood() {
        var user = new com.example.demo.entities.UserEntity();
        user.setId(9L);
        when(userService.getOrThrow(9L)).thenReturn(user);
        when(foodRepository.existsById(1L)).thenReturn(false);

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.addFavorite(1L, 9L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void addFavorite_noopWhenAlreadyFavorite() {
        var user = new com.example.demo.entities.UserEntity();
        user.setId(9L);
        when(userService.getOrThrow(9L)).thenReturn(user);
        when(foodRepository.existsById(1L)).thenReturn(true);
        when(favoriteFoodRepository.existsByUserIdAndFoodId(9L, 1L)).thenReturn(true);

        foodService.addFavorite(1L, 9L);

        verify(favoriteFoodRepository, never()).save(any());
    }

    @Test
    void addFavorite_persistsWhenNew() {
        var user = new com.example.demo.entities.UserEntity();
        user.setId(9L);
        when(userService.getOrThrow(9L)).thenReturn(user);
        when(foodRepository.existsById(1L)).thenReturn(true);
        when(favoriteFoodRepository.existsByUserIdAndFoodId(9L, 1L)).thenReturn(false);

        foodService.addFavorite(1L, 9L);

        verify(favoriteFoodRepository).save(any(FavoriteFoodEntity.class));
    }

    @Test
    void addFavorite_rejectsMissingUser() {
        when(userService.getOrThrow(9L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.addFavorite(1L, 9L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(foodRepository, favoriteFoodRepository);
    }

    @Test
    void addFood_rejectsMissingServingSize() {
        var request = new FoodEntity.FoodRequest(
                "Name",
                "Brand",
                null,
                FoodEntity.Unit.G,
                null,
                10f,
                200f,
                20f,
                10f,
                5f,
                null,
                null,
                null,
                null,
                false
        );

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.addFood(request, 5L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(foodRepository, userService, foodDedupService, aldiPriceFetcher, lidlPriceFetcher, dunnesPriceFetcher, tescoPriceFetcher);
    }

    @Test
    void addFood_rejectsMissingUser() {
        var request = baseRequest(null, null, false);

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.addFood(request, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(foodRepository, userService, foodDedupService, aldiPriceFetcher, lidlPriceFetcher, dunnesPriceFetcher, tescoPriceFetcher, commentService, favoriteFoodRepository);
    }

    @Test
    void removeFavorite_requiresExistingUser() {
        when(userService.getOrThrow(9L)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.removeFavorite(1L, 9L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
        verifyNoInteractions(foodRepository, favoriteFoodRepository);
    }

    @Test
    void deleteFood_forbidsNonOwnerNonAdmin() {
        var food = new FoodEntity();
        food.setId(5L);
        food.setUserId(2L);
        when(foodRepository.findById(5L)).thenReturn(Optional.of(food));

        var requester = new com.example.demo.entities.UserEntity();
        requester.setId(3L);
        requester.setAdmin(false);
        when(userService.get(3L)).thenReturn(Optional.of(requester));

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.deleteFood(5L, 3L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(foodRepository, never()).deleteById(any());
    }

    @Test
    void deleteFood_rejectsMissingUser() {
        var ex = assertThrows(ResponseStatusException.class, () -> foodService.deleteFood(5L, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(foodRepository, userService, favoriteFoodRepository, commentService);
    }

    @Test
    void deleteFood_adminDeletesAndCleansUp() {
        var food = new FoodEntity();
        food.setId(5L);
        food.setUserId(2L);
        when(foodRepository.findById(5L)).thenReturn(Optional.of(food));

        var admin = new com.example.demo.entities.UserEntity();
        admin.setId(3L);
        admin.setAdmin(true);
        when(userService.get(3L)).thenReturn(Optional.of(admin));

        foodService.deleteFood(5L, 3L);

        verify(favoriteFoodRepository).deleteAllByFoodId(5L);
        verify(commentService).deleteAllForFood(5L);
        verify(foodRepository).deleteById(5L);
    }

    @Test
    void getFavorites_returnsDtosInFavoriteOrderAndSetsFlag() {
        var fav1 = new FavoriteFoodEntity();
        fav1.setFoodId(10L);
        var fav2 = new FavoriteFoodEntity();
        fav2.setFoodId(11L);
        when(favoriteFoodRepository.findAllByUserIdOrderByCreatedAtDesc(7L)).thenReturn(List.of(fav1, fav2));

        var foodA = new FoodEntity();
        foodA.setId(10L);
        foodA.setUserId(7L);
        foodA.setName("A");
        var foodB = new FoodEntity();
        foodB.setId(11L);
        foodB.setUserId(99L);
        foodB.setName("B");
        when(foodRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(foodA, foodB));

        var user = new com.example.demo.entities.UserEntity();
        user.setId(7L);
        user.setAdmin(false);
        when(userService.getOrThrow(7L)).thenReturn(user);

        var favorites = foodService.getFavorites(7L);

        assertEquals(List.of(10L, 11L), favorites.stream().map(com.example.demo.DTOs.FoodDto::getId).toList());
        assertTrue(favorites.get(0).getIsFavorite());
        assertTrue(favorites.get(1).getIsFavorite());
        assertEquals(7L, favorites.get(0).getUserId()); // owner visible
        assertNull(favorites.get(1).getUserId()); // not owner, not admin
    }

    @Test
    void getAllFoods_hidesUserIdForPublicRequests() {
        var food = new FoodEntity();
        food.setId(1L);
        food.setUserId(99L);
        food.setName("Soup");
        when(foodRepository.findAll()).thenReturn(List.of(food));

        var foods = foodService.getAllFoods(null);

        assertEquals(1L, foods.getFirst().getId());
        assertNull(foods.getFirst().getUserId());
        verifyNoInteractions(userService);
    }

    @Test
    void getFoodAsDto_usesAuthenticatedViewerWhenPresent() {
        var food = new FoodEntity();
        food.setId(2L);
        food.setUserId(7L);
        food.setName("Salad");
        when(foodRepository.findById(2L)).thenReturn(Optional.of(food));

        var user = new com.example.demo.entities.UserEntity();
        user.setId(7L);
        user.setAdmin(false);
        when(userService.getOrThrow(7L)).thenReturn(user);

        var dto = foodService.getFoodAsDto(2L, 7L);

        assertEquals(2L, dto.getId());
        assertEquals(7L, dto.getUserId());
        verify(userService).getOrThrow(7L);
    }

    @Test
    void addFood_fetchesPriceFromAldiWhenUrlMatches() {
        var request = baseRequest("https://www.aldi.ie/p123", null, false);
        when(foodDedupService.computeCanonicalProductKey(any())).thenReturn(Optional.empty());
        when(foodDedupService.findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(foodDedupService.isPossibleDuplicate(any())).thenReturn(false);
        when(aldiPriceFetcher.fetchProductData(request.productUrl())).thenReturn(Optional.of(
                new com.example.demo.AldiPriceFetcher.AldiProductData(
                        4.55f,
                        List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(inv -> {
            FoodEntity f = inv.getArgument(0);
            f.setId(77L);
            return f;
        });
        var user = new com.example.demo.entities.UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.getOrThrow(5L)).thenReturn(user);

        var dto = foodService.addFood(request, 5L);

        assertEquals(77L, dto.getId());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(4.55f, captor.getValue().getPrice());
        assertEquals("ALDI_API", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id"),
                captor.getValue().getImageUrls());
        assertEquals(List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id"),
                dto.getImageUrls());
    }

    @Test
    void addFood_fetchesPriceAndImageFromLidlWhenUrlMatches() {
        var request = baseRequest("https://www.lidl.ie/p10052806", null, false);
        when(foodDedupService.computeCanonicalProductKey(any())).thenReturn(Optional.empty());
        when(foodDedupService.findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(foodDedupService.isPossibleDuplicate(any())).thenReturn(false);
        when(lidlPriceFetcher.fetchProductData(request.productUrl())).thenReturn(Optional.of(
                new com.example.demo.LidlPriceFetcher.LidlProductData(
                        5.79f,
                        List.of("https://imgproxy-retcat.assets.schwarz/second-image.png")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(inv -> {
            FoodEntity f = inv.getArgument(0);
            f.setId(76L);
            return f;
        });
        var user = new com.example.demo.entities.UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.getOrThrow(5L)).thenReturn(user);

        var dto = foodService.addFood(request, 5L);

        assertEquals(76L, dto.getId());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.79f, captor.getValue().getPrice());
        assertEquals("LIDL_HTML", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://imgproxy-retcat.assets.schwarz/second-image.png"),
                captor.getValue().getImageUrls());
        assertEquals(List.of("https://imgproxy-retcat.assets.schwarz/second-image.png"),
                dto.getImageUrls());
    }

    @Test
    void addFood_leavesImageUrlsEmptyWhenAldiFetchFails() {
        var request = baseRequest("https://www.aldi.ie/p123", null, false);
        when(foodDedupService.computeCanonicalProductKey(any())).thenReturn(Optional.empty());
        when(foodDedupService.findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(foodDedupService.isPossibleDuplicate(any())).thenReturn(false);
        when(aldiPriceFetcher.fetchProductData(request.productUrl())).thenReturn(Optional.empty());
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(inv -> {
            FoodEntity f = inv.getArgument(0);
            f.setId(78L);
            return f;
        });
        var user = new com.example.demo.entities.UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.getOrThrow(5L)).thenReturn(user);

        var dto = foodService.addFood(request, 5L);

        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(List.of(), captor.getValue().getImageUrls());
        assertEquals(List.of(), dto.getImageUrls());
    }

    @Test
    void addFood_skipsStoreFetchersWhenUrlDoesNotMatchKnownStore() {
        var request = baseRequest("https://example.com/product", null, false);
        when(foodDedupService.computeCanonicalProductKey(any())).thenReturn(Optional.empty());
        when(foodDedupService.findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(foodDedupService.isPossibleDuplicate(any())).thenReturn(false);
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(inv -> {
            FoodEntity f = inv.getArgument(0);
            f.setId(81L);
            return f;
        });
        var user = new com.example.demo.entities.UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.getOrThrow(5L)).thenReturn(user);

        var dto = foodService.addFood(request, 5L);

        verifyNoInteractions(aldiPriceFetcher, lidlPriceFetcher, dunnesPriceFetcher, tescoPriceFetcher);
        assertEquals(List.of(), dto.getImageUrls());
    }

    @Test
    void addFood_fetchesPriceAndImageFromTescoWhenUrlMatches() {
        var request = baseRequest("https://www.tesco.ie/groceries/en-IE/products/123456789", null, false);
        when(foodDedupService.computeCanonicalProductKey(any())).thenReturn(Optional.empty());
        when(foodDedupService.findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(foodDedupService.isPossibleDuplicate(any())).thenReturn(false);
        when(tescoPriceFetcher.fetchProductData(request.productUrl())).thenReturn(Optional.of(
                new com.example.demo.TescoPriceFetcher.TescoProductData(
                        3.39f,
                        List.of("https://digitalcontent.api.tesco.com/v2/media/ghs/8091222d-4197-4804-ae13-4060234eb78e/04c4905f-4457-4065-a13a-afe3ce22dc55_2026692084.jpeg")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(inv -> {
            FoodEntity f = inv.getArgument(0);
            f.setId(79L);
            return f;
        });
        var user = new com.example.demo.entities.UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.getOrThrow(5L)).thenReturn(user);

        var dto = foodService.addFood(request, 5L);

        assertEquals(79L, dto.getId());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(3.39f, captor.getValue().getPrice());
        assertEquals("TESCO_GQL", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://digitalcontent.api.tesco.com/v2/media/ghs/8091222d-4197-4804-ae13-4060234eb78e/04c4905f-4457-4065-a13a-afe3ce22dc55_2026692084.jpeg"),
                captor.getValue().getImageUrls());
        assertEquals(List.of("https://digitalcontent.api.tesco.com/v2/media/ghs/8091222d-4197-4804-ae13-4060234eb78e/04c4905f-4457-4065-a13a-afe3ce22dc55_2026692084.jpeg"),
                dto.getImageUrls());
    }

    @Test
    void addFood_fetchesPriceAndImagesFromDunnesWhenUrlMatches() {
        var request = baseRequest("https://www.dunnesstoresgrocery.com/rsid/1234/departments/snacks/id-100806253", null, false);
        when(foodDedupService.computeCanonicalProductKey(any())).thenReturn(Optional.empty());
        when(foodDedupService.findLikelyDuplicates(any(), any(), any(), any(), any(), any(), any())).thenReturn(List.of());
        when(foodDedupService.isPossibleDuplicate(any())).thenReturn(false);
        when(dunnesPriceFetcher.fetchProductData(request.productUrl())).thenReturn(Optional.of(
                new com.example.demo.DunnesPriceFetcher.DunnesProductData(
                        2.49f,
                        List.of("https://images.cdn.dunnesstoresgrocery.com/zoom/100806253_1.jpg")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(inv -> {
            FoodEntity f = inv.getArgument(0);
            f.setId(80L);
            return f;
        });
        var user = new com.example.demo.entities.UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.getOrThrow(5L)).thenReturn(user);

        var dto = foodService.addFood(request, 5L);

        assertEquals(80L, dto.getId());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(2.49f, captor.getValue().getPrice());
        assertEquals("DUNNES_HTML", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://images.cdn.dunnesstoresgrocery.com/zoom/100806253_1.jpg"),
                captor.getValue().getImageUrls());
        assertEquals(List.of("https://images.cdn.dunnesstoresgrocery.com/zoom/100806253_1.jpg"),
                dto.getImageUrls());
    }

    @Test
    void update_forbidsNonOwnerNonAdmin() {
        var existing = new FoodEntity();
        existing.setId(10L);
        existing.setUserId(2L);
        when(foodRepository.findById(10L)).thenReturn(Optional.of(existing));

        var requester = new com.example.demo.entities.UserEntity();
        requester.setId(3L);
        requester.setAdmin(false);
        when(userService.get(3L)).thenReturn(Optional.of(requester));

        var updateReq = new FoodEntity.UpdateRequest(
                10L,
                "New",
                "Brand",
                100f, // servingSize
                FoodEntity.Unit.G,
                1f,   // price
                10f,  // servingsPerContainer
                200f, // caloriesPerServing
                20f,  // proteinPerServing
                10f,  // carbsPerServing
                5f,   // fatPerServing
                "info",
                null,
                null,
                null
        );

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.update(updateReq, 3L));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(foodRepository, never()).save(any());
    }

    @Test
    void update_rejectsMissingId() {
        var updateReq = new FoodEntity.UpdateRequest(
                null,
                "New",
                "Brand",
                100f,
                FoodEntity.Unit.G,
                1f,
                10f,
                200f,
                20f,
                10f,
                5f,
                "info",
                null,
                null,
                null
        );

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.update(updateReq, 3L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(foodRepository, userService, foodDedupService);
    }

    @Test
    void update_rejectsMissingServingSize() {
        var updateReq = new FoodEntity.UpdateRequest(
                10L,
                "New",
                "Brand",
                null,
                FoodEntity.Unit.G,
                1f,
                10f,
                200f,
                20f,
                10f,
                5f,
                "info",
                null,
                null,
                null
        );

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.update(updateReq, 3L));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(foodRepository, userService, foodDedupService);
    }

    @Test
    void update_rejectsMissingUser() {
        var updateReq = new FoodEntity.UpdateRequest(
                10L,
                "New",
                "Brand",
                100f,
                FoodEntity.Unit.G,
                1f,
                10f,
                200f,
                20f,
                10f,
                5f,
                "info",
                null,
                null,
                null
        );

        var ex = assertThrows(ResponseStatusException.class, () -> foodService.update(updateReq, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verifyNoInteractions(foodRepository, userService, foodDedupService);
    }

    @Test
    void update_setsManualPriceMetadataAndCanonicalKey() {
        var existing = new FoodEntity();
        existing.setId(10L);
        existing.setUserId(2L);
        when(foodRepository.findById(10L)).thenReturn(Optional.of(existing));

        var owner = new com.example.demo.entities.UserEntity();
        owner.setId(2L);
        owner.setAdmin(false);
        when(userService.get(2L)).thenReturn(Optional.of(owner));

        when(foodDedupService.computeCanonicalProductKey("http://example.com/p1")).thenReturn(Optional.of("urlhash:abcd"));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        var updateReq = new FoodEntity.UpdateRequest(
                10L,
                "New",
                "Brand",
                100f, // servingSize
                FoodEntity.Unit.G,
                2.99f, // price
                5f,    // servingsPerContainer
                150f,  // caloriesPerServing
                30f,   // proteinPerServing
                10f,   // carbsPerServing
                5f,    // fatPerServing
                "info",
                null,
                null,
                "http://example.com/p1"
        );

        var dto = foodService.update(updateReq, 2L);

        assertEquals("New", dto.getName());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals("urlhash:abcd", captor.getValue().getCanonicalProductKey());
        assertEquals("MANUAL", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
    }
}
