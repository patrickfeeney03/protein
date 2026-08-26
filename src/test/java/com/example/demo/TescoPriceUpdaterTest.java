package com.example.demo;

import com.example.demo.entities.FoodEntity;
import com.example.demo.repositories.FoodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TescoPriceUpdaterTest {

    private static final String IMAGE_URL =
            "https://digitalcontent.api.tesco.com/v2/media/ghs/8091222d-4197-4804-ae13-4060234eb78e/04c4905f-4457-4065-a13a-afe3ce22dc55_2026692084.jpeg";

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private TescoPriceFetcher priceFetcher;

    @InjectMocks
    private TescoPriceUpdater tescoPriceUpdater;

    @Test
    void refreshPrices_updatesImageUrlsWhenPriceIsUnchanged() {
        var food = new FoodEntity();
        food.setId(20L);
        food.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        food.setPrice(3.39f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(3.39f, List.of(IMAGE_URL))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = tescoPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
        assertEquals(3.39f, captor.getValue().getPrice());
    }

    @Test
    void refreshPrices_updatesPriceMetadataAndImageUrlsWhenPriceChanges() {
        var food = new FoodEntity();
        food.setId(21L);
        food.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        food.setPrice(2.99f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(2f);
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(3.39f, List.of(IMAGE_URL))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = tescoPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(3.39f, captor.getValue().getPrice());
        assertEquals("TESCO_GQL", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
        assertEquals((10f * 2f) / 3.39f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceAndImageUrlsAreUnchanged() {
        var food = new FoodEntity();
        food.setId(22L);
        food.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        food.setPrice(3.39f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(3.39f, List.of(IMAGE_URL))
        ));

        var updatedCount = tescoPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceMatchesAtCentPrecision() {
        var food = new FoodEntity();
        food.setId(23L);
        food.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        food.setPrice(3.39f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(3.3900002f, List.of(IMAGE_URL))
        ));

        var updatedCount = tescoPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenImageUrlsOnlyDifferByOrderOrDuplicates() {
        var secondImage = "https://digitalcontent.api.tesco.com/v2/media/ghs/00000000-0000-0000-0000-000000000000/00000000-0000-0000-0000-000000000000_0000000000.jpeg";
        var food = new FoodEntity();
        food.setId(24L);
        food.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        food.setPrice(3.39f);
        food.setImageUrls(List.of(secondImage, IMAGE_URL, IMAGE_URL));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(3.39f, List.of(IMAGE_URL, secondImage))
        ));

        var updatedCount = tescoPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_withFixedClock_setsExactPriceLastVerifiedAt() {
        var food = new FoodEntity();
        food.setId(50L);
        food.setProductUrl("https://www.tesco.ie/groceries/en-IE/products/123456789");
        food.setPrice(2.99f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new TescoPriceFetcher.TescoProductData(3.39f, List.of(IMAGE_URL))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var fixedInstant = Instant.parse("2025-12-01T10:00:00Z");
        var fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        var updatedCount = tescoPriceUpdater.refreshPrices(fixedClock);

        assertEquals(1, updatedCount);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(fixedInstant, captor.getValue().getPriceLastVerifiedAt());
    }
}
