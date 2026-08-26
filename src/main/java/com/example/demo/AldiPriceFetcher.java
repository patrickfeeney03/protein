package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AldiPriceFetcher {
    private static final Pattern SKU_PATTERN = Pattern.compile("-(\\d+)$");
    private static final String DEFAULT_BASE_URL = "https://api.aldi.ie";
    private static final String API_TEMPLATE = "%s/v2/products/%s?servicePoint=D105&serviceType=walk-in";
    private static final Pattern WIDTH_PLACEHOLDER = Pattern.compile("\\{width}");
    private static final Pattern TRAILING_SLUG_PLACEHOLDER = Pattern.compile("/\\{slug}$");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final Logger LOGGER = LoggerFactory.getLogger(AldiPriceFetcher.class);

    public AldiPriceFetcher() {
        this.restTemplate = new RestTemplate();
        this.baseUrl = DEFAULT_BASE_URL;
    }

    AldiPriceFetcher(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public record AldiProductData(Float price, List<String> imageUrls) implements ProductData {
        public AldiProductData {
            imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        }
    }

    public Optional<Float> fetchPrice(String productUrl) {
        return fetchProductData(productUrl).flatMap(data -> Optional.ofNullable(data.price()));
    }

    public Optional<AldiProductData> fetchProductData(String productUrl) {
        if (!RetailerUrlPolicy.isAllowedRetailerUrl(productUrl, RetailerUrlPolicy.Retailer.ALDI)) {
            LOGGER.debug("Rejected non-allowlisted Aldi URL");
            return Optional.empty();
        }
        try {
            return fetchProductDataFromApi(productUrl);
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch Aldi product data for url={}", productUrl, e);
            return Optional.empty();
        }
    }

    private Optional<AldiProductData> fetchProductDataFromApi(String productUrl) {
        var skuOpt = extractSkuFromUrl(productUrl);
        if (skuOpt.isEmpty()) {
            return Optional.empty();
        }

        var apiUrl = API_TEMPLATE.formatted(baseUrl, skuOpt.get());
        try {
            String body = restTemplate.getForObject(apiUrl, String.class);
            if (body == null || body.isBlank()) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(body);

            Float price;
            var dataPriceAmount = node.at("/data/price/amount");
            if (!dataPriceAmount.isMissingNode() && dataPriceAmount.isNumber()) {
                price = normalizeCents(dataPriceAmount.asDouble());
            } else {
                price = extractAmountFromNode(node).flatMap(this::normalizeAmount).orElse(null);
            }

            var imageUrls = extractImageUrls(node.at("/data/assets"));
            if (price == null && imageUrls.isEmpty()) {
                return Optional.empty();
            }

            return Optional.of(new AldiProductData(price, imageUrls));
        } catch (Exception e) {
            LOGGER.debug("Failed to fetch Aldi product data from API url={}", apiUrl, e);
            return Optional.empty();
        }
    }

    private Optional<String> extractSkuFromUrl(String productUrl) {
        if (productUrl == null) {
            return Optional.empty();
        }
        Matcher matcher = SKU_PATTERN.matcher(productUrl);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<Float> parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            var normalized = raw.replace(",", ".");
            return Optional.of(Float.parseFloat(normalized));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<Float> extractAmountFromNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                var price = extractAmountFromNode(item);
                if (price.isPresent()) {
                    return price;
                }
            }
            return Optional.empty();
        }

        if (node.isObject()) {
            if (node.has("amount") && (node.has("currencyCode") || node.has("currency"))) {
                var amountValue = node.get("amount").asText(null);
                return parsePrice(amountValue);
            }

            if (node.has("price") && node.get("price").isObject()) {
                var price = extractAmountFromNode(node.get("price"));
                if (price.isPresent()) {
                    return price;
                }
            }

            var fields = node.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                var price = extractAmountFromNode(entry.getValue());
                if (price.isPresent()) {
                    return price;
                }
            }
        }

        return Optional.empty();
    }

    private Optional<Float> normalizeAmount(Float amount) {
        if (amount == null) {
            return Optional.empty();
        }
        if (amount >= 100 && amount == Math.floor(amount)) {
            return Optional.of(normalizeCents(amount));
        }
        return Optional.of(amount);
    }

    private float normalizeCents(double amount) {
        return (float) (amount / 100.0);
    }

    private List<String> extractImageUrls(JsonNode assetsNode) {
        if (assetsNode == null || !assetsNode.isArray()) {
            return List.of();
        }

        var imageUrls = new ArrayList<String>();
        for (JsonNode assetNode : assetsNode) {
            var url = assetNode.path("url").asText(null);
            var maxWidth = assetNode.path("maxWidth").asText(null);
            if (url == null || url.isBlank() || maxWidth == null || maxWidth.isBlank()) {
                continue;
            }
            imageUrls.add(materializeImageUrl(url, maxWidth));
        }
        return imageUrls;
    }

    String materializeImageUrl(String urlTemplate, String maxWidth) {
        var widthReplaced = WIDTH_PLACEHOLDER.matcher(urlTemplate).replaceAll(maxWidth);
        return TRAILING_SLUG_PLACEHOLDER.matcher(widthReplaced).replaceFirst("");
    }
}
