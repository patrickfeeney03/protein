package com.example.demo.services;

import com.example.demo.AldiPriceFetcher;
import com.example.demo.DunnesPriceFetcher;
import com.example.demo.LidlPriceFetcher;
import com.example.demo.TescoPriceFetcher;
import com.example.demo.entities.FoodEntity;
import com.example.demo.entities.UserEntity;
import com.example.demo.repositories.FoodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceRefreshServiceTest {

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private UserService userService;

    @Mock
    private AldiPriceFetcher aldiPriceFetcher;

    @Mock
    private LidlPriceFetcher lidlPriceFetcher;

    @Mock
    private DunnesPriceFetcher dunnesPriceFetcher;

    @Mock
    private TescoPriceFetcher tescoPriceFetcher;

    private static final Instant FIXED_NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneId.of("UTC"));

    @InjectMocks
    private PriceRefreshService priceRefreshService;

    @Test
    void refreshFood_forbidsNonAdmin() {
        var user = new UserEntity();
        user.setId(5L);
        user.setAdmin(false);
        when(userService.get(5L)).thenReturn(Optional.of(user));

        var ex = assertThrows(ResponseStatusException.class, () -> priceRefreshService.refreshFood(10L, 5L));

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void refreshFood_treatsMissingAdminAsUnauthorized() {
        when(userService.get(5L)).thenReturn(Optional.empty());

        var ex = assertThrows(ResponseStatusException.class, () -> priceRefreshService.refreshFood(10L, 5L));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(foodRepository, never()).findById(any());
    }

    private PriceRefreshService createServiceWithFixedClock() {
        return new PriceRefreshService(foodRepository, userService, aldiPriceFetcher,
                lidlPriceFetcher, dunnesPriceFetcher, tescoPriceFetcher, FIXED_CLOCK);
    }

    @Test
    void refreshFood_updatesSupportedFoodForAdmin() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(4.99f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(2f);
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));
        when(aldiPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(
                        5.99f,
                        List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createServiceWithFixedClock().refreshFood(10L, 1L);

        assertEquals(10L, result.foodId());
        assertEquals("aldi", result.store());
        assertEquals(true, result.updated());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.99f, captor.getValue().getPrice());
        assertEquals("ALDI_API", captor.getValue().getPriceSource());
        assertEquals(FIXED_NOW, captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id"),
                captor.getValue().getImageUrls());
    }

    @Test
    void refreshFood_rejectsUnsupportedProductUrl() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://example.com/product/10");
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));

        var ex = assertThrows(ResponseStatusException.class, () -> priceRefreshService.refreshFood(10L, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshFood_nullRequesterId_throwsUnauthorized() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> priceRefreshService.refreshFood(10L, null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(foodRepository, never()).findById(any());
    }

    @Test
    void refreshAllFoods_nullRequesterId_throwsUnauthorized() {
        var ex = assertThrows(ResponseStatusException.class,
                () -> priceRefreshService.refreshAllFoods(null));
        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        verify(foodRepository, never()).findByProductUrlIsNotNull();
    }

    @Test
    void refreshFood_foodNotFound_throwsNotFound() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));
        when(foodRepository.findById(99L)).thenReturn(Optional.empty());

        var ex = assertThrows(ResponseStatusException.class,
                () -> priceRefreshService.refreshFood(99L, 1L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void refreshFood_aldiFetchFailed_returnsNotUpdated() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.aldi.ie/product/test");
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));
        when(aldiPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.empty());

        var result = priceRefreshService.refreshFood(10L, 1L);
        assertEquals(10L, result.foodId());
        assertEquals("aldi", result.store());
        assertFalse(result.updated());
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshFood_lidlFetchFailed_returnsNotUpdated() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.lidl.ie/product/test");
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));
        when(lidlPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.empty());

        var result = priceRefreshService.refreshFood(10L, 1L);
        assertEquals("lidl", result.store());
        assertFalse(result.updated());
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshFood_dunnesFetchFailed_returnsNotUpdated() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/product/test");
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));
        when(dunnesPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.empty());

        var result = priceRefreshService.refreshFood(10L, 1L);
        assertEquals("dunnes", result.store());
        assertFalse(result.updated());
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshFood_tescoFetchFailed_returnsNotUpdated() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.tesco.ie/groceries/product/test");
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));
        when(tescoPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.empty());

        var result = priceRefreshService.refreshFood(10L, 1L);
        assertEquals("tesco", result.store());
        assertFalse(result.updated());
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshFood_priceAndImagesUnchanged_skipsSave() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.aldi.ie/product/test");
        food.setPrice(4.99f);
        food.setImageUrls(List.of("https://example.com/img.jpg"));
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));
        when(aldiPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(4.99f, List.of("https://example.com/img.jpg"))));

        var result = priceRefreshService.refreshFood(10L, 1L);
        assertEquals("aldi", result.store());
        assertFalse(result.updated());
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshFood_imagesChanged_priceUnchanged_savesUpdate() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.aldi.ie/product/test");
        food.setPrice(4.99f);
        food.setImageUrls(List.of("https://example.com/old.jpg"));
        when(foodRepository.findById(10L)).thenReturn(Optional.of(food));
        when(aldiPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(4.99f, List.of("https://example.com/new.jpg"))));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createServiceWithFixedClock().refreshFood(10L, 1L);
        assertEquals("aldi", result.store());
        assertTrue(result.updated());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(List.of("https://example.com/new.jpg"), captor.getValue().getImageUrls());
        assertEquals(4.99f, captor.getValue().getPrice());
        assertEquals(FIXED_NOW, captor.getValue().getPriceLastVerifiedAt());
    }

    @Test
    void refreshAllFoods_emptyList_returnsZeroCounts() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of());

        var result = priceRefreshService.refreshAllFoods(1L);

        assertEquals(0, result.checked());
        assertEquals(0, result.updated());
    }

    @Test
    void refreshAllFoods_allFetchableFoodsFail_returnsCheckedButNotUpdated() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var aldiFood = new FoodEntity();
        aldiFood.setId(10L);
        aldiFood.setProductUrl("https://www.aldi.ie/product/test");

        var lidlFood = new FoodEntity();
        lidlFood.setId(11L);
        lidlFood.setProductUrl("https://www.lidl.ie/product/test");

        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(aldiFood, lidlFood));
        when(aldiPriceFetcher.fetchProductData(aldiFood.getProductUrl())).thenReturn(Optional.empty());
        when(lidlPriceFetcher.fetchProductData(lidlFood.getProductUrl())).thenReturn(Optional.empty());

        var result = priceRefreshService.refreshAllFoods(1L);

        assertEquals(2, result.checked());
        assertEquals(0, result.updated());
    }

    @Test
    void refreshFood_successfulUpdateForLidl() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(20L);
        food.setProductUrl("https://www.lidl.ie/product/test");
        food.setPrice(4.99f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(2f);
        when(foodRepository.findById(20L)).thenReturn(Optional.of(food));
        when(lidlPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(5.79f, List.of("https://example.com/lidl.jpg"))));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createServiceWithFixedClock().refreshFood(20L, 1L);

        assertEquals(20L, result.foodId());
        assertEquals("lidl", result.store());
        assertTrue(result.updated());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.79f, captor.getValue().getPrice());
        assertEquals("LIDL_HTML", captor.getValue().getPriceSource());
        assertEquals(FIXED_NOW, captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://example.com/lidl.jpg"), captor.getValue().getImageUrls());
    }

    @Test
    void refreshFood_successfulUpdateForDunnes() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(21L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/product/test");
        food.setPrice(3.00f);
        food.setProteinPerServing(8f);
        food.setServingsPerContainer(3f);
        when(foodRepository.findById(21L)).thenReturn(Optional.of(food));
        when(dunnesPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new DunnesPriceFetcher.DunnesProductData(3.39f, List.of("https://example.com/dunnes.jpg"))));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createServiceWithFixedClock().refreshFood(21L, 1L);

        assertEquals(21L, result.foodId());
        assertEquals("dunnes", result.store());
        assertTrue(result.updated());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(3.39f, captor.getValue().getPrice());
        assertEquals("DUNNES_HTML", captor.getValue().getPriceSource());
        assertEquals(FIXED_NOW, captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://example.com/dunnes.jpg"), captor.getValue().getImageUrls());
    }

    @Test
    void refreshFood_successfulUpdateForTesco() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var food = new FoodEntity();
        food.setId(22L);
        food.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        food.setPrice(2.99f);
        food.setProteinPerServing(12f);
        food.setServingsPerContainer(4f);
        when(foodRepository.findById(22L)).thenReturn(Optional.of(food));
        when(tescoPriceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(3.39f, List.of("https://example.com/tesco.jpg"))));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createServiceWithFixedClock().refreshFood(22L, 1L);

        assertEquals(22L, result.foodId());
        assertEquals("tesco", result.store());
        assertTrue(result.updated());
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(3.39f, captor.getValue().getPrice());
        assertEquals("TESCO_GQL", captor.getValue().getPriceSource());
        assertEquals(FIXED_NOW, captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://example.com/tesco.jpg"), captor.getValue().getImageUrls());
    }

    @Test
    void refreshAllFoods_countsCheckedAndUpdatedFoods() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var aldiFood = new FoodEntity();
        aldiFood.setId(10L);
        aldiFood.setProductUrl("https://www.aldi.ie/product/test-388035");
        aldiFood.setPrice(4.99f);

        var tescoFood = new FoodEntity();
        tescoFood.setId(11L);
        tescoFood.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        tescoFood.setPrice(3.39f);
        tescoFood.setImageUrls(List.of("https://existing.example/tesco.jpg"));

        var unsupportedFood = new FoodEntity();
        unsupportedFood.setId(12L);
        unsupportedFood.setProductUrl("https://example.com/product/12");

        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(aldiFood, tescoFood, unsupportedFood));
        when(aldiPriceFetcher.fetchProductData(aldiFood.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(
                        5.99f,
                        List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id")
                )));
        when(tescoPriceFetcher.fetchProductData(tescoFood.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(
                        3.39f,
                        List.of("https://existing.example/tesco.jpg")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createServiceWithFixedClock().refreshAllFoods(1L);

        assertEquals(2, result.checked());
        assertEquals(1, result.updated());
    }

    @Test
    void refreshAllFoods_usesInjectedClock_forAllSavedEntities() {
        var admin = new UserEntity();
        admin.setId(1L);
        admin.setAdmin(true);
        when(userService.get(1L)).thenReturn(Optional.of(admin));

        var aldiFood = new FoodEntity();
        aldiFood.setId(10L);
        aldiFood.setProductUrl("https://www.aldi.ie/product/test-388035");
        aldiFood.setPrice(4.99f);
        aldiFood.setProteinPerServing(10f);
        aldiFood.setServingsPerContainer(2f);

        var lidlFood = new FoodEntity();
        lidlFood.setId(11L);
        lidlFood.setProductUrl("https://www.lidl.ie/product/test");
        lidlFood.setPrice(3.49f);
        lidlFood.setProteinPerServing(8f);
        lidlFood.setServingsPerContainer(4f);

        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(aldiFood, lidlFood));
        when(aldiPriceFetcher.fetchProductData(aldiFood.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(5.99f, List.of("https://example.com/aldi.jpg"))));
        when(lidlPriceFetcher.fetchProductData(lidlFood.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(4.29f, List.of("https://example.com/lidl.jpg"))));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = createServiceWithFixedClock().refreshAllFoods(1L);

        assertEquals(2, result.checked());
        assertEquals(2, result.updated());

        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository, times(2)).save(captor.capture());
        for (var saved : captor.getAllValues()) {
            assertEquals(FIXED_NOW, saved.getPriceLastVerifiedAt());
        }
    }
}
