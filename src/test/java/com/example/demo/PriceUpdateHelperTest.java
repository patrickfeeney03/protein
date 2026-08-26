package com.example.demo;

import com.example.demo.entities.FoodEntity;
import com.example.demo.repositories.FoodRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PriceUpdateHelperTest {

    @Mock
    private FoodRepository foodRepository;

    @Mock
    private Logger logger;

    private static final String IMAGE_URL = "https://example.com/image.jpg";

    private ProductData productData(Float price, List<String> imageUrls) {
        return new ProductData() {
            @Override
            public Float price() {
                return price;
            }

            @Override
            public List<String> imageUrls() {
                return imageUrls;
            }
        };
    }

    @Test
    void applyFetchedData_noChange_returnsFalse() {
        var food = new FoodEntity();
        food.setId(1L);
        food.setPrice(5.99f);
        food.setImageUrls(List.of(IMAGE_URL));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.99f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertFalse(result);
        verify(foodRepository, never()).save(any());
    }

    @Test
    void applyFetchedData_priceChange_returnsTrue() {
        var food = new FoodEntity();
        food.setId(2L);
        food.setPrice(4.99f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.99f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.99f, captor.getValue().getPrice());
        assertEquals("TEST", captor.getValue().getPriceSource());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
    }

    @Test
    void applyFetchedData_imageChange_returnsTrue() {
        var food = new FoodEntity();
        food.setId(3L);
        food.setPrice(5.99f);
        food.setImageUrls(List.of());
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.99f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
        assertNotNull(captor.getValue().getPriceLastVerifiedAt());
    }

    @Test
    void applyFetchedData_bothPriceAndImageChange_returnsTrue() {
        var food = new FoodEntity();
        food.setId(4L);
        food.setPrice(4.99f);
        food.setImageUrls(List.of());
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.99f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.99f, captor.getValue().getPrice());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
    }

    @Test
    void applyFetchedData_nullNewPrice_doesNotSetPrice() {
        var food = new FoodEntity();
        food.setId(5L);
        food.setPrice(5.99f);
        food.setImageUrls(List.of());
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(null, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(5.99f, captor.getValue().getPrice());
        assertNull(captor.getValue().getPriceSource());
        assertNull(captor.getValue().getPriceLastVerifiedAt());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
    }

    @Test
    void applyFetchedData_nullImageUrls_handlesGracefully() {
        var food = new FoodEntity();
        food.setId(6L);
        food.setPrice(5.99f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(6.99f, null),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(6.99f, captor.getValue().getPrice());
        assertEquals(List.of(), captor.getValue().getImageUrls());
    }

    @Test
    void applyFetchedData_priceChangeComputesProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(7L);
        food.setPrice(4.00f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(4f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.00f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals((10f * 4f) / 5.00f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_priceChangeWithZeroPrice_skipsProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(8L);
        food.setPrice(4.00f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(4f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(0f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertNull(captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_priceChangeWithNullProtein_skipsProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(9L);
        food.setPrice(4.00f);
        food.setProteinPerServing(null);
        food.setServingsPerContainer(4f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.00f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertNull(captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_priceChangeWithNullServings_skipsProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(10L);
        food.setPrice(4.00f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(null);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.00f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertNull(captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_priceChange_overwritesExistingProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(20L);
        food.setPrice(2.00f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(4f);
        food.setProteinPerEuro(20.0f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.00f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals((10f * 4f) / 5.00f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_imageOnlyChange_preservesExistingProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(21L);
        food.setPrice(5.00f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(4f);
        food.setProteinPerEuro(8.0f);
        food.setImageUrls(List.of("old-image"));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.00f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(8.0f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_priceChangeWithNullProtein_preservesExistingProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(22L);
        food.setPrice(4.00f);
        food.setProteinPerServing(null);
        food.setServingsPerContainer(4f);
        food.setProteinPerEuro(8.0f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.00f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(8.0f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_priceChangeWithZeroPrice_preservesExistingProteinPerEuro() {
        var food = new FoodEntity();
        food.setId(23L);
        food.setPrice(4.00f);
        food.setProteinPerServing(10f);
        food.setServingsPerContainer(4f);
        food.setProteinPerEuro(8.0f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(0f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(8.0f, captor.getValue().getProteinPerEuro());
    }

    @Test
    void applyFetchedData_imagesUnchangedButPriceNull_returnsFalse() {
        var food = new FoodEntity();
        food.setId(11L);
        food.setPrice(null);
        food.setImageUrls(List.of(IMAGE_URL));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(null, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertFalse(result);
        verify(foodRepository, never()).save(any());
    }

    @Test
    void applyFetchedData_emptyToEmptyImages_returnsFalse() {
        var food = new FoodEntity();
        food.setId(12L);
        food.setPrice(5.99f);
        food.setImageUrls(List.of());

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.99f, List.of()),
                "TEST", "Test", logger, foodRepository);

        assertFalse(result);
        verify(foodRepository, never()).save(any());
    }

    @Test
    void applyFetchedData_imageOnlyChange_bothPricesNull_doesNotSetPriceLastVerifiedAt() {
        var food = new FoodEntity();
        food.setId(13L);
        food.setPrice(null);
        food.setImageUrls(List.of());
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(null, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertNull(captor.getValue().getPriceLastVerifiedAt());
        assertNull(captor.getValue().getPrice());
        assertNull(captor.getValue().getPriceSource());
        assertEquals(List.of(IMAGE_URL), captor.getValue().getImageUrls());
    }

    @Test
    void refreshPrices_updatesOnlyMatchingDomain() {
        var food1 = new FoodEntity();
        food1.setId(1L);
        food1.setProductUrl("https://www.aldi.ie/product/123");
        food1.setPrice(5.99f);
        food1.setImageUrls(List.of("img1"));

        var food2 = new FoodEntity();
        food2.setId(2L);
        food2.setProductUrl("https://www.tesco.ie/product/456");
        food2.setPrice(3.39f);
        food2.setImageUrls(List.of("img2"));

        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food1, food2));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var count = PriceUpdateHelper.refreshPrices(
                foodRepository,
                url -> java.util.Optional.of(productData(4.99f, List.of("new-img"))),
                "aldi.ie",
                "TEST",
                "Aldi",
                logger);

        assertEquals(1, count);
        verify(foodRepository).save(any());
    }

    @Test
    void refreshPrices_skipsWhenFetcherReturnsEmpty() {
        var food = new FoodEntity();
        food.setId(3L);
        food.setProductUrl("https://www.aldi.ie/product/123");
        food.setPrice(5.99f);
        food.setImageUrls(List.of("img"));

        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));

        var count = PriceUpdateHelper.refreshPrices(
                foodRepository,
                url -> java.util.Optional.empty(),
                "aldi.ie",
                "TEST",
                "Aldi",
                logger);

        assertEquals(0, count);
        verify(foodRepository, never()).save(any());
    }

    @Test
    void refreshPrices_continuesOnException() {
        var food1 = new FoodEntity();
        food1.setId(4L);
        food1.setProductUrl("https://www.aldi.ie/product/err");
        food1.setPrice(5.99f);
        food1.setImageUrls(List.of("img1"));

        var food2 = new FoodEntity();
        food2.setId(5L);
        food2.setProductUrl("https://www.aldi.ie/product/ok");
        food2.setPrice(4.99f);
        food2.setImageUrls(List.of("img2"));

        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food1, food2));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var count = PriceUpdateHelper.refreshPrices(
                foodRepository,
                url -> {
                    if (url.contains("err")) {
                        throw new RuntimeException("fetch failed");
                    }
                    return java.util.Optional.of(productData(3.99f, List.of("new-img")));
                },
                "aldi.ie",
                "TEST",
                "Aldi",
                logger);

        assertEquals(1, count);
        verify(foodRepository).save(any());
    }

    @Test
    void refreshPrices_multipleMatchingFoods_allUpdated() {
        var food1 = new FoodEntity();
        food1.setId(6L);
        food1.setProductUrl("https://www.aldi.ie/product/one");
        food1.setPrice(5.99f);
        food1.setImageUrls(List.of("img1"));

        var food2 = new FoodEntity();
        food2.setId(7L);
        food2.setProductUrl("https://www.aldi.ie/product/two");
        food2.setPrice(3.49f);
        food2.setImageUrls(List.of("img2"));

        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food1, food2));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var count = PriceUpdateHelper.refreshPrices(
                foodRepository,
                url -> java.util.Optional.of(productData(2.99f, List.of("new"))),
                "aldi.ie",
                "TEST",
                "Aldi",
                logger);

        assertEquals(2, count);
    }

    @Test
    void applyFetchedData_atSpringForwardBoundary_setsExactPriceLastVerifiedAt() {
        var springForwardInstant = Instant.parse("2026-03-29T00:30:00Z");
        var dublinClock = Clock.fixed(springForwardInstant, ZoneId.of("Europe/Dublin"));

        var food = new FoodEntity();
        food.setId(30L);
        food.setPrice(4.99f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.99f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository, dublinClock);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(springForwardInstant, captor.getValue().getPriceLastVerifiedAt());
    }

    @Test
    void applyFetchedData_atFallBackBoundary_setsExactPriceLastVerifiedAt() {
        var fallBackInstant = Instant.parse("2026-10-25T01:30:00Z");
        var dublinClock = Clock.fixed(fallBackInstant, ZoneId.of("Europe/Dublin"));

        var food = new FoodEntity();
        food.setId(31L);
        food.setPrice(4.99f);
        food.setImageUrls(List.of(IMAGE_URL));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var result = PriceUpdateHelper.applyFetchedData(
                food, productData(5.99f, List.of(IMAGE_URL)),
                "TEST", "Test", logger, foodRepository, dublinClock);

        assertTrue(result);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(fallBackInstant, captor.getValue().getPriceLastVerifiedAt());
    }

    @Test
    void refreshPrices_withDSTZone_setsExactPriceLastVerifiedAt() {
        var springForwardInstant = Instant.parse("2026-03-29T00:30:00Z");
        var dublinClock = Clock.fixed(springForwardInstant, ZoneId.of("Europe/Dublin"));

        var food = new FoodEntity();
        food.setId(32L);
        food.setProductUrl("https://www.aldi.ie/product/123");
        food.setPrice(5.99f);
        food.setImageUrls(List.of("img1"));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var count = PriceUpdateHelper.refreshPrices(
                foodRepository,
                url -> Optional.of(productData(4.99f, List.of("new-img"))),
                "aldi.ie",
                "TEST",
                "Aldi",
                logger,
                dublinClock);

        assertEquals(1, count);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(springForwardInstant, captor.getValue().getPriceLastVerifiedAt());
    }

    @Test
    void refreshPrices_withFixedClock_setsExactPriceLastVerifiedAt() {
        var food = new FoodEntity();
        food.setId(1L);
        food.setProductUrl("https://www.aldi.ie/product/123");
        food.setPrice(5.99f);
        food.setImageUrls(List.of("img1"));
        when(foodRepository.findByProductUrlIsNotNull()).thenReturn(List.of(food));
        when(foodRepository.save(any(FoodEntity.class))).thenAnswer(i -> i.getArgument(0));

        var fixedInstant = Instant.parse("2025-12-01T10:00:00Z");
        var fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"));

        var count = PriceUpdateHelper.refreshPrices(
                foodRepository,
                url -> Optional.of(productData(4.99f, List.of("new-img"))),
                "aldi.ie",
                "TEST",
                "Aldi",
                logger,
                fixedClock);

        assertEquals(1, count);
        var captor = ArgumentCaptor.forClass(FoodEntity.class);
        verify(foodRepository).save(captor.capture());
        assertEquals(fixedInstant, captor.getValue().getPriceLastVerifiedAt());
    }
}
