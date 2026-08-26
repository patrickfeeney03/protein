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
class LidlPriceUpdaterTest {

    private static final String IMAGE_URL = "https://imgproxy-retcat.assets.schwarz/second-image.png";

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private LidlPriceFetcher priceFetcher;

    @InjectMocks
    private LidlPriceUpdater lidlPriceUpdater;

    @Test
    void refreshPrices_updatesImageUrlsWhenPriceIsUnchanged() {
        var food = new FoodEntity();
        food.setId(40L);
        food.setProductUrl("https://www.lidl.ie/p10052806");
        food.setPrice(5.79f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(5.79f, List.of(IMAGE_URL))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = lidlPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
        assertEquals(5.79f, captor.getValue().getPrice());
    }

    @Test
    void refreshPrices_updatesPriceMetadataAndImageUrlsWhenPriceChanges() {
        var food = new FoodEntity();
        food.setId(41L);
        food.setProductUrl("https://www.lidl.ie/p10052806");
        food.setPrice(4.99f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(2f);
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(5.79f, List.of(IMAGE_URL))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = lidlPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.79f, captor.getValue().getPrice());
        assertEquals("LIDL_HTML", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
        assertEquals((10f * 2f) / 5.79f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceAndImageUrlsAreUnchanged() {
        var food = new FoodEntity();
        food.setId(42L);
        food.setProductUrl("https://www.lidl.ie/p10052806");
        food.setPrice(5.79f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(5.79f, List.of(IMAGE_URL))
        ));

        var updatedCount = lidlPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceMatchesAtCentPrecision() {
        var food = new FoodEntity();
        food.setId(43L);
        food.setProductUrl("https://www.lidl.ie/p10052806");
        food.setPrice(5.79f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(5.7900002f, List.of(IMAGE_URL))
        ));

        var updatedCount = lidlPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenImageUrlsOnlyDifferByOrderOrDuplicates() {
        var secondImage = "https://imgproxy-retcat.assets.schwarz/first-image.png";
        var food = new FoodEntity();
        food.setId(44L);
        food.setProductUrl("https://www.lidl.ie/p10052806");
        food.setPrice(5.79f);
        food.setImageUrls(List.of(secondImage, IMAGE_URL, IMAGE_URL));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(5.79f, List.of(IMAGE_URL, secondImage))
        ));

        var updatedCount = lidlPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_withFixedClock_setsExactPriceLastVerifiedAt() {
        var food = new FoodEntity();
        food.setId(50L);
        food.setProductUrl("https://www.lidl.ie/p10052806");
        food.setPrice(4.99f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new LidlPriceFetcher.LidlProductData(5.79f, List.of(IMAGE_URL))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var fixedInstant = Instant.parse("2025-12-01T10:00:00Z");
        var fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        var updatedCount = lidlPriceUpdater.refreshPrices(fixedClock);

        assertEquals(1, updatedCount);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(fixedInstant, captor.getValue().getPriceLastVerifiedAt());
    }
}
