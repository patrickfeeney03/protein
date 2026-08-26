package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LidlPriceFetcherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LidlPriceFetcher fetcher = new LidlPriceFetcher();

    @Test
    void extractImageUrls_prefersSecondImageListEntry() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "items": [
                    {
                      "gridbox": {
                        "data": {
                          "image": "https://imgproxy-retcat.assets.schwarz/fallback-image.png",
                          "imageList": [
                            "https://imgproxy-retcat.assets.schwarz/first-image.png",
                            "https://imgproxy-retcat.assets.schwarz/second-image.png"
                          ]
                        }
                      }
                    }
                  ]
                }
                """);

        var imageUrls = fetcher.extractImageUrls(root);

        assertEquals(List.of("https://imgproxy-retcat.assets.schwarz/second-image.png"), imageUrls);
    }

    @Test
    void extractImageUrls_fallsBackToImageWhenSecondImageIsMissing() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "items": [
                    {
                      "gridbox": {
                        "data": {
                          "image": "https://imgproxy-retcat.assets.schwarz/fallback-image.png",
                          "imageList": [
                            "https://imgproxy-retcat.assets.schwarz/first-image.png"
                          ]
                        }
                      }
                    }
                  ]
                }
                """);

        var imageUrls = fetcher.extractImageUrls(root);

        assertEquals(List.of("https://imgproxy-retcat.assets.schwarz/fallback-image.png"), imageUrls);
    }

    @Test
    void extractRegularPrice_usesRootWrapperPricePath() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "root": {
                    "items": [
                      {
                        "gridbox": {
                          "data": {
                            "lidlPlus": [
                              {
                                "price": {
                                  "oldPrice": "5.79"
                                }
                              }
                            ]
                          }
                        }
                      }
                    ]
                  }
                }
                """);

        var price = fetcher.extractRegularPrice(root);

        assertTrue(price.isPresent());
        assertEquals(5.79f, price.orElseThrow(), 0.0001f);
    }

    @Test
    void extractRegularPrice_usesItemsWrapperPricePath() throws Exception {
        var root = objectMapper.readTree("""
                {
                  "items": [
                    {
                      "gridbox": {
                        "data": {
                          "lidlPlus": [
                            {
                              "price": {
                                "discount": {
                                  "deletedPrice": "4.49"
                                }
                              }
                            }
                          ]
                        }
                      }
                    }
                  ]
                }
                """);

        var price = fetcher.extractRegularPrice(root);

        assertTrue(price.isPresent());
        assertEquals(4.49f, price.orElseThrow(), 0.0001f);
    }
}
