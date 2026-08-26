package com.example.demo;

import com.example.demo.DTOs.AldiCategoryApiResponse;
import com.example.demo.DTOs.CommonScrappedDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AldiFoodScraperTest {

    private static final String API_URL = "https://api.aldi.ie/v3/product-search?currency=EUR&serviceType=walk-in&categoryKey=1588161416978075005&limit=30&offset=0&sort=relevance&servicePoint=D105";

    @Mock
    private RestTemplate restTemplate;

    private AldiFoodScraper scraper;

    private AldiFoodScraper createScraper() {
        return new AldiFoodScraper(restTemplate);
    }

    // --- getStoreName ---

    @Test
    void getStoreName_returnsAldi() {
        scraper = createScraper();
        assertEquals("Aldi", scraper.getStoreName());
    }

    // --- scrapeRaw ---

    @Test
    void scrapeRaw_returnsProducts() {
        var products = List.of(
                new AldiCategoryApiResponse.AldiProduct("sku1", "name1", "brand1", null, "0.5 kg",
                        new AldiCategoryApiResponse.Price(499, "EUR"),
                        List.of(new AldiCategoryApiResponse.Category("cat1", "Cat1", null)),
                        List.of(new AldiCategoryApiResponse.Asset("https://example.com/img/{width}/slug", 1500, 1500, "image/jpeg"))),
                new AldiCategoryApiResponse.AldiProduct("sku2", "name2", "brand2", null, "1 kg",
                        new AldiCategoryApiResponse.Price(899, "EUR"),
                        List.of(),
                        List.of())
        );
        var response = new AldiCategoryApiResponse(null, products);
        when(restTemplate.getForObject(API_URL, AldiCategoryApiResponse.class)).thenReturn(response);

        scraper = createScraper();
        var result = scraper.scrapeRaw();

        assertEquals(2, result.size());
        assertEquals("sku1", result.get(0).sku());
        assertEquals("sku2", result.get(1).sku());
    }

    @Test
    void scrapeRaw_nullResponse_returnsEmptyList() {
        when(restTemplate.getForObject(API_URL, AldiCategoryApiResponse.class)).thenReturn(null);

        scraper = createScraper();
        var result = scraper.scrapeRaw();

        assertEquals(List.of(), result);
    }

    @Test
    void scrapeRaw_nullData_returnsEmptyList() {
        var response = new AldiCategoryApiResponse(null, null);
        when(restTemplate.getForObject(API_URL, AldiCategoryApiResponse.class)).thenReturn(response);

        scraper = createScraper();
        var result = scraper.scrapeRaw();

        assertEquals(List.of(), result);
    }

    @Test
    void scrapeRaw_emptyData_returnsEmptyList() {
        var response = new AldiCategoryApiResponse(null, List.of());
        when(restTemplate.getForObject(API_URL, AldiCategoryApiResponse.class)).thenReturn(response);

        scraper = createScraper();
        var result = scraper.scrapeRaw();

        assertEquals(List.of(), result);
    }

    @Test
    void scrapeRaw_httpError_returnsEmptyList() {
        when(restTemplate.getForObject(API_URL, AldiCategoryApiResponse.class))
                .thenThrow(new RestClientException("timeout"));

        scraper = createScraper();
        var result = scraper.scrapeRaw();

        assertEquals(List.of(), result);
    }

    // --- mapToCommonScrapped ---

    @Test
    void mapToCommonScrapped_fullMapping() {
        var asset = new AldiCategoryApiResponse.Asset("https://example.com/img/{width}/slug", 1500, 1500, "image/jpeg");
        var category = new AldiCategoryApiResponse.Category("cat1", "Dairy", null);
        var product = new AldiCategoryApiResponse.AldiProduct("sku-123", "Milk 2L", "SuperBrand", null, "0.5 kg",
                new AldiCategoryApiResponse.Price(299, "EUR"),
                List.of(category),
                List.of(asset));

        scraper = createScraper();
        var result = scraper.mapToCommonScrapped(product);

        assertNotNull(result);
        assertEquals(List.of("https://example.com/img/1500/slug"), result.images());
        assertEquals("SuperBrand", result.brand());
        assertEquals(List.of("Dairy"), result.categories());
        assertEquals("Milk 2L", result.name());
        assertEquals(299f, result.price());
        assertEquals(500f, result.weight()); // 0.5 kg -> 500g
        assertEquals("https://www.aldi.ie/product/sku-123", result.link());
    }

    @Test
    void mapToCommonScrapped_sellingSizeNull_returnsNull() {
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, null,
                new AldiCategoryApiResponse.Price(100, "EUR"), List.of(), List.of());

        scraper = createScraper();
        assertNull(scraper.mapToCommonScrapped(product));
    }

    @Test
    void mapToCommonScrapped_sellingSizeBlank_returnsNull() {
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "",
                new AldiCategoryApiResponse.Price(100, "EUR"), List.of(), List.of());

        scraper = createScraper();
        assertNull(scraper.mapToCommonScrapped(product));
    }

    @Test
    void mapToCommonScrapped_sellingSizeNoNumberMatch_returnsNull() {
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "no numbers here",
                new AldiCategoryApiResponse.Price(100, "EUR"), List.of(), List.of());

        scraper = createScraper();
        assertNull(scraper.mapToCommonScrapped(product));
    }

    @Test
    void mapToCommonScrapped_sellingSizeNonParseableNumber_returnsNull() {
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "abc kg",
                new AldiCategoryApiResponse.Price(100, "EUR"), List.of(), List.of());

        scraper = createScraper();
        assertNull(scraper.mapToCommonScrapped(product));
    }

    @Test
    void mapToCommonScrapped_assetUrlReplacesWidthPlaceholder() {
        var asset = new AldiCategoryApiResponse.Asset("https://cdn.aldi.ie/product/{width}/img.jpg", 800, 600, "image/jpeg");
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "1 kg",
                new AldiCategoryApiResponse.Price(200, "EUR"), List.of(), List.of(asset));

        scraper = createScraper();
        var result = scraper.mapToCommonScrapped(product);

        assertNotNull(result);
        assertEquals(List.of("https://cdn.aldi.ie/product/800/img.jpg"), result.images());
    }

    @Test
    void mapToCommonScrapped_multipleAssets() {
        var asset1 = new AldiCategoryApiResponse.Asset("https://example.com/img/{width}/a.jpg", 500, 500, "image/jpeg");
        var asset2 = new AldiCategoryApiResponse.Asset("https://example.com/img/{width}/b.jpg", 1000, 1000, "image/jpeg");
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "0.25 kg",
                new AldiCategoryApiResponse.Price(150, "EUR"), List.of(), List.of(asset1, asset2));

        scraper = createScraper();
        var result = scraper.mapToCommonScrapped(product);

        assertNotNull(result);
        assertEquals(List.of("https://example.com/img/500/a.jpg", "https://example.com/img/1000/b.jpg"), result.images());
    }

    @Test
    void mapToCommonScrapped_categoriesMappedToNames() {
        var cat1 = new AldiCategoryApiResponse.Category("c1", "Beverages", null);
        var cat2 = new AldiCategoryApiResponse.Category("c2", "Dairy", null);
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "1 kg",
                new AldiCategoryApiResponse.Price(100, "EUR"), List.of(cat1, cat2), List.of());

        scraper = createScraper();
        var result = scraper.mapToCommonScrapped(product);

        assertNotNull(result);
        assertEquals(List.of("Beverages", "Dairy"), result.categories());
    }

    @Test
    void mapToCommonScrapped_noCategories_returnsEmptyList() {
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "1 kg",
                new AldiCategoryApiResponse.Price(100, "EUR"), List.of(), List.of());

        scraper = createScraper();
        var result = scraper.mapToCommonScrapped(product);

        assertNotNull(result);
        assertEquals(List.of(), result.categories());
    }

    @Test
    void mapToCommonScrapped_noAssets_returnsEmptyImages() {
        var product = new AldiCategoryApiResponse.AldiProduct("sku1", "name", "brand", null, "1 kg",
                new AldiCategoryApiResponse.Price(100, "EUR"), List.of(), List.of());

        scraper = createScraper();
        var result = scraper.mapToCommonScrapped(product);

        assertNotNull(result);
        assertEquals(List.of(), result.images());
    }

    // --- getData ---

    @Test
    void getData_filtersNullMappings() {
        var valid = new AldiCategoryApiResponse.AldiProduct("sku1", "Good Milk", "BrandA", null, "0.5 kg",
                new AldiCategoryApiResponse.Price(200, "EUR"),
                List.of(), List.of());
        var nullSellingSize = new AldiCategoryApiResponse.AldiProduct("sku2", "Bad Item", "BrandB", null, null,
                new AldiCategoryApiResponse.Price(300, "EUR"),
                List.of(), List.of());

        var response = new AldiCategoryApiResponse(null, List.of(valid, nullSellingSize));
        when(restTemplate.getForObject(API_URL, AldiCategoryApiResponse.class)).thenReturn(response);

        scraper = createScraper();
        var result = scraper.getData();

        assertEquals(1, result.size());
        assertEquals("Good Milk", result.get(0).name());
        assertEquals("BrandA", result.get(0).brand());
    }

    @Test
    void getData_scrapeFails_returnsEmptyList() {
        when(restTemplate.getForObject(API_URL, AldiCategoryApiResponse.class))
                .thenThrow(new RestClientException("timeout"));

        scraper = createScraper();
        var result = scraper.getData();

        assertEquals(List.of(), result);
    }
}
