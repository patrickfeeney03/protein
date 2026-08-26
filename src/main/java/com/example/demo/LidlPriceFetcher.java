package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class LidlPriceFetcher {
    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("/p(\\d+)$");
    private static final String DEFAULT_BASE_URL = "https://www.lidl.ie";
    private static final String API_TEMPLATE = "%s/q/api/search?q=%s&assortment=IE&locale=en_IE&redirect=false&version=v2.1.0";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final Logger logger = LoggerFactory.getLogger(LidlPriceFetcher.class);

    public LidlPriceFetcher() {
        this.restTemplate = new RestTemplate();
        this.baseUrl = DEFAULT_BASE_URL;
    }

    LidlPriceFetcher(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public record LidlProductData(Float price, List<String> imageUrls) implements ProductData {
        public LidlProductData {
            imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        }
    }

    public Optional<Float> fetchPrice(String productUrl) {
        return fetchProductData(productUrl).flatMap(data -> Optional.ofNullable(data.price()));
    }

    public Optional<LidlProductData> fetchProductData(String productUrl) {
        if (!RetailerUrlPolicy.isAllowedRetailerUrl(productUrl, RetailerUrlPolicy.Retailer.LIDL)) {
            logger.debug("Rejected non-allowlisted Lidl URL");
            return Optional.empty();
        }
        try {
            String cleanedUrl = stripFragmentAndQuery(productUrl);
            var productId = extractProductId(cleanedUrl);
            if (productId.isEmpty()) {
                logger.debug("Lidl product id not found url={}", cleanedUrl);
                return Optional.empty();
            }

            var apiUrl = API_TEMPLATE.formatted(baseUrl, productId.get());
            String body = restTemplate.getForObject(URI.create(apiUrl), String.class);
            if (body == null || body.isBlank()) {
                logger.debug("Lidl empty response apiUrl={}", apiUrl);
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(body);
            var price = extractRegularPrice(root);
            var imageUrls = extractImageUrls(root);
            if (price.isPresent() || !imageUrls.isEmpty()) {
                return Optional.of(new LidlProductData(price.orElse(null), imageUrls));
            }

            logger.debug("Lidl product data not found apiUrl={}", apiUrl);
            return Optional.empty();
        } catch (Exception e) {
            logger.debug("Failed to fetch Lidl product data for url={}", productUrl, e);
            return Optional.empty();
        }
    }

    private String stripFragmentAndQuery(String url) {
        try {
            URI uri = new URI(url);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null).toString();
        } catch (Exception e) {
            return url;
        }
    }

    Optional<Float> extractRegularPrice(JsonNode root) {
        var regularPrice = firstPresentPrice(root,
                "/root/items/0/gridbox/data/lidlPlus/0/price/oldPrice",
                "/root/items/0/gridbox/data/lidlPlus/0/price/discount/deletedPrice",
                "/items/0/gridbox/data/lidlPlus/0/price/oldPrice",
                "/items/0/gridbox/data/lidlPlus/0/price/discount/deletedPrice"
        );
        if (regularPrice.isPresent()) {
            return regularPrice;
        }

        var fallbackOldPrice = firstPresentPrice(root,
                "/root/items/0/gridbox/data/price/oldPrice",
                "/items/0/gridbox/data/price/oldPrice"
        );
        if (fallbackOldPrice.isPresent() && fallbackOldPrice.get() > 0) {
            return fallbackOldPrice;
        }

        return firstPresentPrice(root,
                "/root/items/0/gridbox/data/price/price",
                "/items/0/gridbox/data/price/price"
        );
    }

    private Optional<Float> firstPresentPrice(JsonNode root, String... paths) {
        for (String path : paths) {
            var price = extractPriceFromPriceNode(root.at(path));
            if (price.isPresent()) {
                return price;
            }
        }
        return Optional.empty();
    }

    private Optional<Float> extractPriceFromPriceNode(JsonNode priceNode) {
        if (priceNode == null || priceNode.isNull()) {
            return Optional.empty();
        }
        if (priceNode.isNumber()) {
            return Optional.of((float) priceNode.asDouble());
        }
        if (priceNode.isTextual()) {
            return parsePrice(priceNode.asText(null));
        }
        if (priceNode.isObject()) {
            for (String field : new String[] { "oldPrice", "regularPrice", "price", "currentPrice", "value", "amount" }) {
                var node = priceNode.get(field);    
                if (node == null || node.isNull()) {
                    continue;
                }
                var parsed = extractPriceFromPriceNode(node);
                if (parsed.isPresent()) {
                    return parsed;
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Float> parsePrice(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            var normalized = raw.replaceAll("[^0-9,\\.]", "").replace(",", ".");
            if (normalized.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Float.parseFloat(normalized));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Optional<String> extractProductId(String url) {
        if (url == null) {
            return Optional.empty();
        }
        var matcher = PRODUCT_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private boolean hasCurrencyFields(JsonNode node) {
        return node.has("displayedCurrency") || node.has("currencySymbol") || node.has("currencyCode");
    }

    List<String> extractImageUrls(JsonNode root) {
        var dataNode = root.at("/root/items/0/gridbox/data");
        if (dataNode.isMissingNode() || dataNode.isNull()) {
            dataNode = root.at("/items/0/gridbox/data");
        }

        var preferredImage = firstNonBlank(
                imageAt(dataNode.at("/imageList"), 1),
                imageAt(dataNode.at("/imageList_V1"), 1),
                dataNode.path("image").asText(null),
                dataNode.at("/image_V1/image").asText(null),
                imageAt(dataNode.at("/imageList"), 0),
                imageAt(dataNode.at("/imageList_V1"), 0)
        );

        return preferredImage.map(List::of).orElseGet(List::of);
    }

    private Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    private String imageAt(JsonNode node, int index) {
        if (node == null || !node.isArray() || node.size() <= index || index < 0) {
            return null;
        }

        var imageNode = node.get(index);
        if (imageNode == null || imageNode.isNull()) {
            return null;
        }
        if (imageNode.isTextual()) {
            return imageNode.asText(null);
        }
        return imageNode.path("image").asText(null);
    }

}
