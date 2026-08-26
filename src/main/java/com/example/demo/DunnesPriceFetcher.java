package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class DunnesPriceFetcher {
    private static final Pattern STORE_ID_PATTERN = Pattern.compile("/rsid/(\\d+)");
    private static final Pattern PRODUCT_ID_PATTERN = Pattern.compile("id-(\\d+)");
    private static final String CHROME_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    private static final double PAGE_TIMEOUT_MS = 15_000;
    private static final double API_FETCH_TIMEOUT_MS = 15_000;
    private static final String DEFAULT_BASE_URL = "https://storefrontgateway.dunnesstoresgrocery.com";
    private static final Set<String> BROWSER_ALLOWED_HOSTS = Set.of(
            "www.dunnesstoresgrocery.com",
            "dunnesstoresgrocery.com",
            "storefrontgateway.dunnesstoresgrocery.com"
    );

    private final Logger logger = LoggerFactory.getLogger(DunnesPriceFetcher.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DunnesPriceFetcher() {
        this.restTemplate = new RestTemplate();
        this.baseUrl = DEFAULT_BASE_URL;
    }

    DunnesPriceFetcher(RestTemplate restTemplate, String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public record DunnesProductData(Float price, List<String> imageUrls) implements ProductData {
        public DunnesProductData {
            imageUrls = imageUrls == null ? List.of() : List.copyOf(imageUrls);
        }
    }

    public Optional<Float> fetchPrice(String productUrl) {
        return fetchProductData(productUrl).flatMap(data -> Optional.ofNullable(data.price()));
    }

    public Optional<DunnesProductData> fetchProductData(String productUrl) {
        if (!RetailerUrlPolicy.isAllowedRetailerUrl(productUrl, RetailerUrlPolicy.Retailer.DUNNES)) {
            logger.debug("Rejected non-allowlisted Dunnes URL");
            return Optional.empty();
        }
        String cleanedUrl = stripFragmentAndQuery(productUrl);
        var storeId = extractStoreId(cleanedUrl);
        var productId = extractProductId(cleanedUrl);
        if (storeId.isEmpty() || productId.isEmpty()) {
            logger.debug("Dunnes store/product id not found url={}", cleanedUrl);
            return Optional.empty();
        }

        var apiUrl = baseUrl + "/api/stores/"
                + storeId.get() + "/products/" + productId.get();

        try {
            String body = restTemplate.getForObject(URI.create(apiUrl), String.class);
            if (body == null || body.isBlank()) {
                logger.debug("Dunnes empty response apiUrl={}", apiUrl);
                return Optional.empty();
            }

            if (looksLikeCloudflareChallenge(body)) {
                logger.info("Dunnes API returned Cloudflare challenge for apiUrl={}, using browser fallback", apiUrl);
                return fetchProductDataWithBrowser(cleanedUrl, apiUrl);
            }

            return parseProductDataBody(body, apiUrl);
        } catch (Exception e) {
            logger.debug("Direct Dunnes fetch failed for url={}, trying browser fallback", productUrl, e);
            return fetchProductDataWithBrowser(cleanedUrl, apiUrl);
        }
    }

    Optional<DunnesProductData> fetchProductDataWithBrowser(String productUrl, String apiUrl) {
        try (Playwright playwright = Playwright.create()) {
            logger.info("Starting Dunnes browser fallback for apiUrl={}", apiUrl);
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(true)
            );
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent(CHROME_USER_AGENT)
                    .setLocale("en-IE")
                    .setServiceWorkers(ServiceWorkerPolicy.BLOCK)
            );
            context.setExtraHTTPHeaders(Map.of(
                    "Accept-Language", "en-IE,en;q=0.9",
                    "Upgrade-Insecure-Requests", "1"
            ));

            Page page = context.newPage();
            page.setDefaultTimeout(PAGE_TIMEOUT_MS);
            page.setDefaultNavigationTimeout(PAGE_TIMEOUT_MS);
            page.route("**/*", route -> {
                if (!RetailerUrlPolicy.isAllowedBrowserUrl(route.request().url(), BROWSER_ALLOWED_HOSTS)) {
                    route.abort();
                    return;
                }
                var resourceType = route.request().resourceType();
                if ("image".equals(resourceType) || "media".equals(resourceType) || "font".equals(resourceType)) {
                    route.abort();
                    return;
                }
                route.resume();
            });

            var nav = page.navigate(
                    productUrl,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
            );
            if (nav != null) {
                logger.debug("Dunnes browser navigate status={} url={}", nav.status(), nav.url());
            }

            waitForChallengeResolution(page, productUrl);

            Object responseText = page.evaluate(
                    "async (apiUrl) => {" +
                            "  const controller = new AbortController();" +
                            "  const timeout = setTimeout(() => controller.abort(), " + (int) API_FETCH_TIMEOUT_MS + ");" +
                            "  try {" +
                            "  const response = await fetch(apiUrl, {" +
                            "    credentials: 'include'," +
                            "    headers: { 'accept': 'application/json, text/plain, */*' }," +
                            "    signal: controller.signal" +
                            "  });" +
                            "  return await response.text();" +
                            "  } finally {" +
                            "    clearTimeout(timeout);" +
                            "  }" +
                            "}",
                    apiUrl
            );

            if (responseText == null) {
                logger.warn("Dunnes browser fallback returned null for apiUrl={}", apiUrl);
                return Optional.empty();
            }

            String body = responseText.toString();
            if (looksLikeCloudflareChallenge(body)) {
                logger.warn("Dunnes browser fallback is still challenged for apiUrl={}", apiUrl);
                return Optional.empty();
            }

            var productData = parseProductDataBody(body, apiUrl);
            if (productData.isPresent()) {
                logger.info("Dunnes browser fallback succeeded for apiUrl={} imageCount={} hasPrice={}",
                        apiUrl,
                        productData.get().imageUrls().size(),
                        productData.get().price() != null);
            } else {
                logger.warn("Dunnes browser fallback returned no usable product data for apiUrl={}", apiUrl);
            }
            return productData;
        } catch (Exception e) {
            logger.warn("Browser Dunnes fetch failed for url={}: {}", productUrl, e.getMessage());
            logger.debug("Browser Dunnes fetch failure details for url={}", productUrl, e);
            return Optional.empty();
        }
    }

    private void waitForChallengeResolution(Page page, String productUrl) {
        try {
            var challenged = Boolean.TRUE.equals(page.evaluate(
                    "() => document.title.includes('Just a moment') || document.body?.innerText?.includes('Enable JavaScript and cookies to continue')"
            ));
            if (!challenged) {
                return;
            }

            logger.info("Dunnes product page shows Cloudflare challenge for url={}, waiting for clearance", productUrl);
            page.waitForFunction(
                    "() => !document.title.includes('Just a moment') && !document.body?.innerText?.includes('Enable JavaScript and cookies to continue')",
                    new Page.WaitForFunctionOptions().setTimeout(PAGE_TIMEOUT_MS)
            );
            page.waitForTimeout(1000);
            logger.info("Dunnes product page challenge cleared for url={}", productUrl);
        } catch (Exception e) {
            logger.warn("Dunnes product page challenge did not clear within {} ms for url={}", (int) PAGE_TIMEOUT_MS, productUrl);
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

    private Optional<String> extractStoreId(String url) {
        var matcher = STORE_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<String> extractProductId(String url) {
        var matcher = PRODUCT_ID_PATTERN.matcher(url);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private Optional<DunnesProductData> parseProductDataBody(String body, String apiUrl) throws Exception {
        JsonNode root = objectMapper.readTree(body);

        var priceNode = root.get("price");
        Float price = null;
        if (priceNode != null && !priceNode.isNull()) {
            price = parsePrice(priceNode.asText()).orElse(null);
        }

        var imageUrls = extractImageUrls(root);
        if (price == null && imageUrls.isEmpty()) {
            logger.debug("Dunnes product data missing apiUrl={}", apiUrl);
            return Optional.empty();
        }

        return Optional.of(new DunnesProductData(price, imageUrls));
    }

    boolean looksLikeCloudflareChallenge(String body) {
        if (body == null || body.isBlank()) {
            return false;
        }
        return body.contains("Just a moment...")
                || body.contains("Enable JavaScript and cookies to continue")
                || body.contains("__cf_chl_");
    }

    List<String> extractImageUrls(JsonNode root) {
        var preferredImage = firstNonBlank(
                root.at("/primaryImage/zoom").asText(null),
                root.at("/primaryImage/details").asText(null),
                root.at("/primaryImage/default").asText(null),
                root.at("/primaryImage/cell").asText(null),
                firstArrayValue(root.at("/additionalImages/zoom")),
                firstArrayValue(root.at("/additionalImages/details")),
                firstArrayValue(root.at("/additionalImages/default")),
                firstArrayValue(root.at("/additionalImages/cell"))
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

    private String firstArrayValue(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }
        for (JsonNode imageNode : node) {
            var imageUrl = imageNode.asText(null);
            if (imageUrl != null && !imageUrl.isBlank()) {
                return imageUrl;
            }
        }
        return null;
    }
}
