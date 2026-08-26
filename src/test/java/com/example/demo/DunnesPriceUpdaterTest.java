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
class DunnesPriceUpdaterTest {

    private static final String PRIMARY_IMAGE =
            "https://images.cdn.dunnesstoresgrocery.com/zoom/100806253_1.jpg";

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private DunnesPriceFetcher priceFetcher;

    @InjectMocks
    private DunnesPriceUpdater dunnesPriceUpdater;

    @Test
    void refreshPrices_updatesImageUrlsWhenPriceIsUnchanged() {
        var food = new FoodEntity();
        food.setId(30L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/rsid/1234/departments/snacks/id-100806253");
        food.setPrice(2.49f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new DunnesPriceFetcher.DunnesProductData(2.49f, List.of(PRIMARY_IMAGE))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = dunnesPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(List.of(PRIMARY_IMAGE), captor.getValue().getImageUrls());
        assertEquals(2.49f, captor.getValue().getPrice());
    }

    @Test
    void refreshPrices_updatesPriceMetadataAndImageUrlsWhenPriceChanges() {
        var food = new FoodEntity();
        food.setId(31L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/rsid/1234/departments/snacks/id-100806253");
        food.setPrice(1.99f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(2f);
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new DunnesPriceFetcher.DunnesProductData(2.49f, List.of(PRIMARY_IMAGE))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = dunnesPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(2.49f, captor.getValue().getPrice());
        assertEquals("DUNNES_HTML", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of(PRIMARY_IMAGE), captor.getValue().getImageUrls());
        assertEquals((10f * 2f) / 2.49f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceAndImageUrlsAreUnchanged() {
        var food = new FoodEntity();
        food.setId(32L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/rsid/1234/departments/snacks/id-100806253");
        food.setPrice(2.49f);
        food.setImageUrls(List.of(PRIMARY_IMAGE));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new DunnesPriceFetcher.DunnesProductData(2.49f, List.of(PRIMARY_IMAGE))
        ));

        var updatedCount = dunnesPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceMatchesAtCentPrecision() {
        var food = new FoodEntity();
        food.setId(33L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/rsid/1234/departments/snacks/id-100806253");
        food.setPrice(2.49f);
        food.setImageUrls(List.of(PRIMARY_IMAGE));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new DunnesPriceFetcher.DunnesProductData(2.4900002f, List.of(PRIMARY_IMAGE))
        ));

        var updatedCount = dunnesPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenImageUrlsOnlyDifferByOrderOrDuplicates() {
        var secondImage = "https://images.cdn.dunnesstoresgrocery.com/cell/100806253_2.jpg";
        var food = new FoodEntity();
        food.setId(34L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/rsid/1234/departments/snacks/id-100806253");
        food.setPrice(2.49f);
        food.setImageUrls(List.of(secondImage, PRIMARY_IMAGE, PRIMARY_IMAGE));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new DunnesPriceFetcher.DunnesProductData(2.49f, List.of(PRIMARY_IMAGE, secondImage))
        ));

        var updatedCount = dunnesPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_withFixedClock_setsExactPriceLastVerifiedAt() {
        var food = new FoodEntity();
        food.setId(50L);
        food.setProductUrl("https://www.dunnesstoresgrocery.com/rsid/1234/departments/snacks/id-100806253");
        food.setPrice(1.99f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new DunnesPriceFetcher.DunnesProductData(2.49f, List.of(PRIMARY_IMAGE))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var fixedInstant = Instant.parse("2025-12-01T10:00:00Z");
        var fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        var updatedCount = dunnesPriceUpdater.refreshPrices(fixedClock);

        assertEquals(1, updatedCount);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(fixedInstant, captor.getValue().getPriceLastVerifiedAt());
    }
}
