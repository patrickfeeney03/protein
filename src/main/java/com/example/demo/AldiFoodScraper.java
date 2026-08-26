package com.example.demo;

import com.example.demo.DTOs.AldiCategoryApiResponse;
import com.example.demo.DTOs.CommonScrappedDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Component
public class AldiFoodScraper implements FoodScraper<AldiCategoryApiResponse.AldiProduct>{

    private final String url = "https://api.aldi.ie/v3/product-search?currency=EUR&serviceType=walk-in&categoryKey=1588161416978075005&limit=30&offset=0&sort=relevance&servicePoint=D105";

    private final RestTemplate restTemplate;
    private final Logger logger = LoggerFactory.getLogger(AldiFoodScraper.class);

    public AldiFoodScraper() {
        this.restTemplate = new RestTemplate();
    }

    AldiFoodScraper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getStoreName() {
        return "Aldi";
    }

    @Override
    public CommonScrappedDTO mapToCommonScrapped(AldiCategoryApiResponse.AldiProduct rawItem) {
        var assets = rawItem.assets().stream().map(a ->
            a.url().replace("{width}", a.maxWidth().toString())).toList();
        var categories = rawItem.categories().stream().map(AldiCategoryApiResponse.Category::name).toList();
        var url = "https://www.aldi.ie/product/" + rawItem.sku();

        if (rawItem.sellingSize() == null || rawItem.sellingSize().isEmpty()) return null;

        var pattern = Pattern.compile("([0-9.]+)\\s*([a-zA-Z]+)");
        var matcher = pattern.matcher(rawItem.sellingSize());

        if (!matcher.find()) return null;

        float rawWeight;
        try {
            rawWeight = Float.parseFloat(matcher.group(1));
        } catch (NumberFormatException e) {
            logger.warn("Aldi scrape failed to parse weight from sellingSize='{}'", rawItem.sellingSize(), e);
            return null;
        }
        var unit = matcher.group(2);

        var weight = rawWeight * 1000; // aldi only seems to have kg

        return new CommonScrappedDTO(
                assets,
                rawItem.brandName(),
                categories,
                rawItem.name(),
                Float.valueOf(rawItem.price().amount()),
                weight,
                url
        );
    }

    @Override
    public List<AldiCategoryApiResponse.AldiProduct> scrapeRaw() {
        try {
            AldiCategoryApiResponse response = this.restTemplate.getForObject(
                    url, AldiCategoryApiResponse.class
            );
            return (response != null && response.data() != null && !response.data().isEmpty()) ?
                    response.data() : List.of();
        } catch (RestClientException e) {
            logger.warn("Aldi scrape HTTP failed url={}", url, e);
            return List.of();
        }
    }

    @Override
    public List<CommonScrappedDTO> getData() {
        var rawResponses = this.scrapeRaw();
        return rawResponses.stream().map(this::mapToCommonScrapped).filter(Objects::nonNull).toList();
    }
}
