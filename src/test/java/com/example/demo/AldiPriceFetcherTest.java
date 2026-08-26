package com.example.demo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AldiPriceFetcherTest {

    private final AldiPriceFetcher fetcher = new AldiPriceFetcher();

    @Test
    void materializeImageUrl_replacesWidthAndRemovesTrailingSlugPlaceholder() {
        var urlTemplate = "https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/{width}/asset-id/{slug}";

        var imageUrl = fetcher.materializeImageUrl(urlTemplate, "1500");

        assertEquals("https://dm.emea.cms.aldi.cx/is/image/aldiprodeu/product/jpg/scaleWidth/1500/asset-id", imageUrl);
    }
}
