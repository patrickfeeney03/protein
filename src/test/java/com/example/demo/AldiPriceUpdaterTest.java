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
class AldiPriceUpdaterTest {

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private AldiPriceFetcher priceFetcher;

    @InjectMocks
    private AldiPriceUpdater aldiPriceUpdater;

    @Test
    void refreshPrices_updatesImageUrlsWhenPriceIsUnchanged() {
        var food = new FoodEntity();
        food.setId(10L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(5.99f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(
                        5.99f,
                        List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = aldiPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id"),
                captor.getValue().getImageUrls());
        assertEquals(5.99f, captor.getValue().getPrice());
    }

    @Test
    void refreshPrices_updatesPriceMetadataAndImageUrlsWhenPriceChanges() {
        var food = new FoodEntity();
        food.setId(11L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(4.99f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(2f);
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(
                        5.99f,
                        List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id")
                )));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var updatedCount = aldiPriceUpdater.refreshPrices();

        assertEquals(1, updatedCount);
        ArgumentCaptor<FoodEntity> captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.99f, captor.getValue().getPrice());
        assertEquals("ALDI_API", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id"),
                captor.getValue().getImageUrls());
        assertEquals((10f * 2f) / 5.99f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceAndImageUrlsAreUnchanged() {
        var imageUrl = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id";
        var food = new FoodEntity();
        food.setId(12L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(5.99f);
        food.setImageUrls(List.of(imageUrl));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(
                        5.99f,
                        List.of(imageUrl)
                )));

        var updatedCount = aldiPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenImageUrlsOnlyDifferByOrderOrDuplicates() {
        var firstImageUrl = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-a";
        var secondImageUrl = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-b";
        var food = new FoodEntity();
        food.setId(13L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(5.99f);
        food.setImageUrls(List.of(secondImageUrl, firstImageUrl, firstImageUrl));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(
                        5.99f,
                        List.of(firstImageUrl, secondImageUrl)
                )));

        var updatedCount = aldiPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_skipsSaveWhenPriceMatchesAtCentPrecision() {
        var imageUrl = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id";
        var food = new FoodEntity();
        food.setId(14L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(5.99f);
        food.setImageUrls(List.of(imageUrl));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(
                        5.9900002f,
                        List.of(imageUrl)
                )));

        var updatedCount = aldiPriceUpdater.refreshPrices();

        assertEquals(0, updatedCount);
        verify(foodRepository, never()).save(any(FoodEntity.class));
    }

    @Test
    void refreshPrices_withDSTZone_setsExactPriceLastVerifiedAt() {
        var springForwardInstant = Instant.parse("2026-03-29T00:30:00Z");
        var dublinClock = Clock.fixed(springForwardInstant, ZoneId.of("Europe/Dublin"));

        var food = new FoodEntity();
        food.setId(60L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(4.99f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(5.99f, List.of("https://example.com/img.jpg"))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var updatedCount = aldiPriceUpdater.refreshPrices(dublinClock);

        assertEquals(1, updatedCount);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(springForwardInstant, captor.getValue().getPriceLastVerifiedAt());
    }

    @Test
    void refreshPrices_withFixedClock_setsExactPriceLastVerifiedAt() {
        var food = new FoodEntity();
        food.setId(50L);
        food.setProductUrl("https://www.aldi.ie/product/test-388035");
        food.setPrice(4.99f);
        food.setImageUrls(List.of());
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(priceFetcher.fetchProductData(food.getProductUrl())).thenReturn(Optional.of(
                new AldiPriceFetcher.AldiProductData(5.99f, List.of("https://example.com/img.jpg"))
        ));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var fixedInstant = Instant.parse("2025-12-01T10:00:00Z");
        var fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        var updatedCount = aldiPriceUpdater.refreshPrices(fixedClock);

        assertEquals(1, updatedCount);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(fixedInstant, captor.getValue().getPriceLastVerifiedAt());
    }
}
