package com.example.demo;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TescoPriceFetcherTest {

    private final TescoPriceFetcher fetcher = new TescoPriceFetcher();

    // --- extractTpnc ---

    @Test
    void extractTpnc_null_returnsEmpty() {
        assertEquals(Optional.empty(), fetcher.extractTpnc(null));
    }

    @Test
    void extractTpnc_blank_returnsEmpty() {
        assertEquals(Optional.empty(), fetcher.extractTpnc("  "));
    }

    @Test
    void extractTpnc_urlWithProductId_returnsTpnc() {
        var result = fetcher.extractTpnc("https://www.tesco.ie/groceries/en-IE/products/317328050");
        assertTrue(result.isPresent());
        assertEquals("317328050", result.get());
    }

    @Test
    void extractTpnc_urlWithoutMatch_returnsEmpty() {
        var result = fetcher.extractTpnc("https://www.tesco.ie/groceries/en-IE/category/123");
        assertEquals(Optional.empty(), result);
    }

    // --- parseProductDataFromResponse ---

    @Test
    void parseProductDataFromResponse_null_returnsEmpty() throws Exception {
        assertEquals(Optional.empty(), fetcher.parseProductDataFromResponse(null, "tpnc1", "url"));
    }

    @Test
    void parseProductDataFromResponse_blank_returnsEmpty() throws Exception {
        assertEquals(Optional.empty(), fetcher.parseProductDataFromResponse("  ", "tpnc1", "url"));
    }

    @Test
    void parseProductDataFromResponse_fullyPopulated_returnsProductData() throws Exception {
        var json = """
                [{
                  "data": {
                    "product": {
                      "price": { "actual": 3.49 },
                      "media": {
                        "defaultImage": { "url": "https://img.tesco.com/img.jpg" }
                      }
                    }
                  }
                }]
                """;

        var result = fetcher.parseProductDataFromResponse(json, "317328050", "url");

        assertTrue(result.isPresent());
        var data = result.get();
        assertEquals(3.49f, data.price(), 0.001f);
        assertEquals(List.of("https://img.tesco.com/img.jpg"), data.imageUrls());
    }

    @Test
    void parseProductDataFromResponse_priceOnly_returnsProductData() throws Exception {
        var json = """
                [{
                  "data": {
                    "product": {
                      "price": { "actual": 5.99 },
                      "media": {}
                    }
                  }
                }]
                """;

        var result = fetcher.parseProductDataFromResponse(json, "tpnc1", "url");

        assertTrue(result.isPresent());
        assertEquals(5.99f, result.get().price(), 0.001f);
        assertTrue(result.get().imageUrls().isEmpty());
    }

    @Test
    void parseProductDataFromResponse_imageOnly_returnsProductData() throws Exception {
        var json = """
                [{
                  "data": {
                    "product": {
                      "media": {
                        "defaultImage": { "url": "https://img.tesco.com/img.jpg" }
                      }
                    }
                  }
                }]
                """;

        var result = fetcher.parseProductDataFromResponse(json, "tpnc1", "url");

        assertTrue(result.isPresent());
        assertEquals(List.of("https://img.tesco.com/img.jpg"), result.get().imageUrls());
    }

    @Test
    void parseProductDataFromResponse_noPriceNoImage_returnsEmpty() throws Exception {
        var json = """
                [{
                  "data": {
                    "product": {
                      "media": {}
                    }
                  }
                }]
                """;

        assertEquals(Optional.empty(), fetcher.parseProductDataFromResponse(json, "tpnc1", "url"));
    }

    @Test
    void parseProductDataFromResponse_priceNodeMissing_returnsEmpty() throws Exception {
        var json = """
                [{
                  "data": {
                    "product": {
                      "title": "Some Product",
                      "media": {}
                    }
                  }
                }]
                """;

        assertEquals(Optional.empty(), fetcher.parseProductDataFromResponse(json, "tpnc1", "url"));
    }

    // --- TescoProductData record ---

    @Test
    void tescoProductData_nullImageUrls_normalizesToEmptyList() {
        var data = new TescoPriceFetcher.TescoProductData(2.99f, null);
        assertEquals(2.99f, data.price(), 0.001f);
        assertTrue(data.imageUrls().isEmpty());
    }

    @Test
    void tescoProductData_nonNullImageUrls_returnsUnmodifiableCopy() {
        var urls = List.of("https://img.tesco.com/a.jpg", "https://img.tesco.com/b.jpg");
        var data = new TescoPriceFetcher.TescoProductData(null, urls);
        assertEquals(urls, data.imageUrls());
    }
}
