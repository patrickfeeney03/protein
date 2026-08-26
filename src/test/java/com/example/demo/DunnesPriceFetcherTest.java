package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DunnesPriceFetcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DunnesPriceFetcher fetcher = new DunnesPriceFetcher();

    @Test
    void extractImageUrls_prefersPrimaryZoom() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "primaryImage": {
                    "default": "https://images.cdn.dunnesstoresgrocery.com/cell/100806253_1.jpg",
                    "zoom": "https://images.cdn.dunnesstoresgrocery.com/zoom/100806253_1.jpg"
                  },
                  "additionalImages": {
                    "default": [
                      "https://storage.googleapis.com/images-dun-prd/product-images/cell/100806253_9.jpg"
                    ]
                  }
                }
                """);

        var imageUrls = fetcher.extractImageUrls(root);

        assertEquals(List.of("https://images.cdn.dunnesstoresgrocery.com/zoom/100806253_1.jpg"), imageUrls);
    }

    @Test
    void extractImageUrls_fallsBackWhenPrimaryZoomIsMissing() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "primaryImage": {
                    "default": "https://images.cdn.dunnesstoresgrocery.com/cell/100806253_1.jpg"
                  },
                  "additionalImages": {
                    "zoom": [
                      "https://storage.googleapis.com/images-dun-prd/product-images/zoom/100806253_9.jpg"
                    ]
                  }
                }
                """);

        var imageUrls = fetcher.extractImageUrls(root);

        assertEquals(List.of("https://images.cdn.dunnesstoresgrocery.com/cell/100806253_1.jpg"), imageUrls);
    }

    @Test
    void looksLikeCloudflareChallenge_detectsChallengeMarkup() {
        var body = """
                <!DOCTYPE html>
                <html>
                <head><title>Just a moment...</title></head>
                <body>Enable JavaScript and cookies to continue</body>
                </html>
                """;

        assertEquals(true, fetcher.looksLikeCloudflareChallenge(body));
    }

    @Test
    void looksLikeCloudflareChallenge_ignoresJson() {
        var body = """
                {
                  "price": "3.39",
                  "primaryImage": {
                    "zoom": "https://images.cdn.dunnesstoresgrocery.com/zoom/100806253_1.jpg"
                  }
                }
                """;

        assertEquals(false, fetcher.looksLikeCloudflareChallenge(body));
    }
}
